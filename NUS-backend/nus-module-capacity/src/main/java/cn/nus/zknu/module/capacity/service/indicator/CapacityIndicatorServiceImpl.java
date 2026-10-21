package cn.nus.zknu.module.capacity.service.indicator;

import cn.hutool.core.util.ObjectUtil;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.module.capacity.controller.admin.indicator.vo.CapacityIndicatorBatchCreateReqVO;
import cn.nus.zknu.module.capacity.controller.admin.indicator.vo.CapacityIndicatorResetWeightReqVO;
import cn.nus.zknu.module.capacity.controller.admin.indicator.vo.CapacityIndicatorSaveReqVO;
import cn.nus.zknu.module.capacity.dal.dataobject.indicator.CapacityIndicatorDO;
import cn.nus.zknu.module.capacity.dal.mysql.indicator.CapacityIndicatorMapper;
import cn.nus.zknu.module.capacity.dal.redis.RedisKeyConstants;
import jakarta.annotation.Resource;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.capacity.enums.ErrorCodeConstants.*;

@Service
public class CapacityIndicatorServiceImpl implements CapacityIndicatorService {

    private static final Integer FIRST_LEVEL = 1;
    private static final Integer SECOND_LEVEL = 2;
    private static final Long ROOT_PARENT_ID = 0L;
    private static final BigDecimal MAX_WEIGHT = new BigDecimal("100");

    @Resource
    private CapacityIndicatorMapper capacityIndicatorMapper;
    @Resource
    private CapacityIndicatorCacheInvalidator cacheInvalidator;

    /**
     * 创建单条培养指标。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createIndicator(CapacityIndicatorSaveReqVO createReqVO) {
        // 一级指标统一挂到 ROOT_PARENT_ID，避免前端传入脏父节点
        normalizeSaveReq(createReqVO);
        // 只允许 1/2 两层指标体系
        validateIndicatorLevel(createReqVO.getIndicatorLevel());
        // 二级指标必须有一级父指标；一级指标不允许挂真实父节点
        validateParentIndicator(null, createReqVO.getIndicatorLevel(), createReqVO.getParentIndicatorId());
        // 新增前先校验当前层级（或当前一级节点下）的权重上限
        validateWeightLimit(null, createReqVO.getIndicatorLevel(), createReqVO.getParentIndicatorId(), createReqVO.getWeight());

        CapacityIndicatorDO indicator = BeanUtils.toBean(createReqVO, CapacityIndicatorDO.class);
        capacityIndicatorMapper.insert(indicator);
        invalidateTreeCache();
        return indicator.getId();
    }

    /**
     * 批量创建培养指标（同层级、同父节点）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchCreateIndicators(CapacityIndicatorBatchCreateReqVO batchCreateReqVO) {
        // 批量新增沿用单条新增的层级与父节点规则
        normalizeBatchCreateReq(batchCreateReqVO);
        validateIndicatorLevel(batchCreateReqVO.getIndicatorLevel());
        validateParentIndicator(null, batchCreateReqVO.getIndicatorLevel(), batchCreateReqVO.getParentIndicatorId());
        // 批量校验总权重，避免出现“前几条成功、最后一条失败”的中间态
        validateBatchCreateWeightLimit(batchCreateReqVO);

        for (CapacityIndicatorBatchCreateReqVO.IndicatorItem item : batchCreateReqVO.getIndicators()) {
            CapacityIndicatorDO indicator = new CapacityIndicatorDO();
            indicator.setIndicatorName(item.getIndicatorName());
            indicator.setIndicatorDetails(item.getIndicatorDetails());
            indicator.setIndicatorLevel(batchCreateReqVO.getIndicatorLevel());
            indicator.setParentIndicatorId(batchCreateReqVO.getParentIndicatorId());
            indicator.setWeight(item.getWeight());
            indicator.setStatus(item.getStatus());
            capacityIndicatorMapper.insert(indicator);
        }
        invalidateTreeCache();
    }

    /**
     * 更新单条培养指标。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateIndicator(CapacityIndicatorSaveReqVO updateReqVO) {
        CapacityIndicatorDO oldIndicator = validateIndicatorExists(updateReqVO.getId());
        normalizeSaveReq(updateReqVO);
        validateIndicatorLevel(updateReqVO.getIndicatorLevel());
        // 编辑时禁止改层级和父节点，避免树结构被破坏
        validateUpdateImmutableFields(oldIndicator, updateReqVO);
        validateParentIndicator(updateReqVO.getId(), updateReqVO.getIndicatorLevel(), updateReqVO.getParentIndicatorId());
        validateWeightLimit(updateReqVO.getId(), updateReqVO.getIndicatorLevel(), updateReqVO.getParentIndicatorId(), updateReqVO.getWeight());

        CapacityIndicatorDO updateObj = BeanUtils.toBean(updateReqVO, CapacityIndicatorDO.class);
        capacityIndicatorMapper.updateById(updateObj);
        invalidateTreeCache();
    }

    /**
     * 批量重设某个作用域内的指标权重。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetIndicatorWeights(CapacityIndicatorResetWeightReqVO resetWeightReqVO) {
        validateIndicatorLevel(resetWeightReqVO.getIndicatorLevel());
        // 一级重设允许 parent=0；二级重设必须指定所属一级指标
        validateResetWeightScope(resetWeightReqVO.getIndicatorLevel(), resetWeightReqVO.getParentIndicatorId());

        List<CapacityIndicatorDO> scopedIndicators = getScopedIndicators(
                resetWeightReqVO.getIndicatorLevel(), resetWeightReqVO.getParentIndicatorId());
        Map<Long, CapacityIndicatorDO> scopedIndicatorMap = new HashMap<>();
        for (CapacityIndicatorDO indicator : scopedIndicators) {
            scopedIndicatorMap.put(indicator.getId(), indicator);
        }

        Map<Long, BigDecimal> weightMap = new HashMap<>();
        for (CapacityIndicatorResetWeightReqVO.IndicatorWeightItem item : resetWeightReqVO.getIndicators()) {
            // 只允许修改当前作用域内的指标权重，避免越权更新其它节点
            if (!scopedIndicatorMap.containsKey(item.getId())) {
                throw exception(INDICATOR_WEIGHT_RESET_SCOPE_ERROR);
            }
            weightMap.put(item.getId(), item.getWeight());
        }

        BigDecimal totalWeight = BigDecimal.ZERO;
        for (CapacityIndicatorDO indicator : scopedIndicators) {
            // 未传的指标保持原值，再统一计算总和做 100% 上限校验
            BigDecimal weight = weightMap.containsKey(indicator.getId())
                    ? weightMap.get(indicator.getId())
                    : ObjectUtil.defaultIfNull(indicator.getWeight(), BigDecimal.ZERO);
            totalWeight = totalWeight.add(ObjectUtil.defaultIfNull(weight, BigDecimal.ZERO));
        }
        if (totalWeight.compareTo(MAX_WEIGHT) > 0) {
            throw exception(Objects.equals(resetWeightReqVO.getIndicatorLevel(), FIRST_LEVEL)
                    ? INDICATOR_FIRST_LEVEL_WEIGHT_EXCEED
                    : INDICATOR_SECOND_LEVEL_WEIGHT_EXCEED);
        }

        for (CapacityIndicatorResetWeightReqVO.IndicatorWeightItem item : resetWeightReqVO.getIndicators()) {
            CapacityIndicatorDO updateObj = new CapacityIndicatorDO();
            updateObj.setId(item.getId());
            updateObj.setWeight(item.getWeight());
            capacityIndicatorMapper.updateById(updateObj);
        }
        invalidateTreeCache();
    }

    /**
     * 删除指标（存在子指标时禁止删除）。
     */
    @Override
    public void deleteIndicator(Long id) {
        validateIndicatorExists(id);
        // 有子指标时禁止删除，保证树结构完整
        if (capacityIndicatorMapper.selectCountByParentIndicatorId(id) > 0) {
            throw exception(INDICATOR_EXISTS_CHILDREN);
        }
        capacityIndicatorMapper.deleteById(id);
        invalidateTreeCache();
    }

    /**
     * 获取指标详情。
     */
    @Override
    public CapacityIndicatorDO getIndicator(Long id) {
        return capacityIndicatorMapper.selectById(id);
    }

    /**
     * 获取指标树列表。
     */
    @Override
    @Cacheable(cacheNames = RedisKeyConstants.INDICATOR_TREE, key = "'tree'")
    public List<CapacityIndicatorDO> getIndicatorList() {
        return capacityIndicatorMapper.selectListForTree();
    }

    private void invalidateTreeCache() {
        if (cacheInvalidator != null) {
            cacheInvalidator.evictAfterCommit();
        }
    }

    /**
     * 规范化单条保存请求中的父节点信息。
     */
    private void normalizeSaveReq(CapacityIndicatorSaveReqVO reqVO) {
        if (Objects.equals(reqVO.getIndicatorLevel(), FIRST_LEVEL)) {
            reqVO.setParentIndicatorId(ROOT_PARENT_ID);
        }
    }

    /**
     * 规范化批量保存请求中的父节点信息。
     */
    private void normalizeBatchCreateReq(CapacityIndicatorBatchCreateReqVO reqVO) {
        if (Objects.equals(reqVO.getIndicatorLevel(), FIRST_LEVEL)) {
            reqVO.setParentIndicatorId(ROOT_PARENT_ID);
        }
    }

    /**
     * 校验批量重设权重的作用域参数是否合法。
     */
    private void validateResetWeightScope(Integer indicatorLevel, Long parentIndicatorId) {
        if (Objects.equals(indicatorLevel, FIRST_LEVEL)) {
            if (parentIndicatorId != null && !ObjectUtil.equal(parentIndicatorId, ROOT_PARENT_ID)) {
                throw exception(INDICATOR_PARENT_ERROR);
            }
            return;
        }
        validateParentIndicator(null, indicatorLevel, parentIndicatorId);
    }

    /**
     * 校验指标层级，仅允许一级/二级。
     */
    private void validateIndicatorLevel(Integer indicatorLevel) {
        if (!Objects.equals(indicatorLevel, FIRST_LEVEL) && !Objects.equals(indicatorLevel, SECOND_LEVEL)) {
            throw exception(INDICATOR_LEVEL_INVALID);
        }
    }

    /**
     * 校验父指标约束关系。
     */
    private void validateParentIndicator(Long id, Integer indicatorLevel, Long parentIndicatorId) {
        if (Objects.equals(indicatorLevel, FIRST_LEVEL)) {
            if (!ObjectUtil.equal(parentIndicatorId, ROOT_PARENT_ID)) {
                throw exception(INDICATOR_PARENT_ERROR);
            }
            return;
        }
        if (parentIndicatorId == null || ObjectUtil.equal(parentIndicatorId, ROOT_PARENT_ID)) {
            throw exception(INDICATOR_PARENT_REQUIRED);
        }
        if (Objects.equals(id, parentIndicatorId)) {
            throw exception(INDICATOR_PARENT_ERROR);
        }
        CapacityIndicatorDO parentIndicator = capacityIndicatorMapper.selectById(parentIndicatorId);
        if (parentIndicator == null) {
            throw exception(INDICATOR_PARENT_NOT_EXISTS);
        }
        if (!Objects.equals(parentIndicator.getIndicatorLevel(), FIRST_LEVEL)) {
            throw exception(INDICATOR_PARENT_NOT_FIRST_LEVEL);
        }
    }

    /**
     * 校验单条新增/编辑后的权重上限。
     */
    private void validateWeightLimit(Long id, Integer indicatorLevel, Long parentIndicatorId, BigDecimal currentWeight) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        // 一级按“全部一级指标”计算；二级按“同父一级下的全部二级指标”计算
        List<CapacityIndicatorDO> indicators = getScopedIndicators(indicatorLevel, parentIndicatorId);
        for (CapacityIndicatorDO indicator : indicators) {
            if (Objects.equals(indicator.getId(), id)) {
                continue;
            }
            if (indicator.getWeight() != null) {
                totalWeight = totalWeight.add(indicator.getWeight());
            }
        }
        totalWeight = totalWeight.add(ObjectUtil.defaultIfNull(currentWeight, BigDecimal.ZERO));
        if (totalWeight.compareTo(MAX_WEIGHT) > 0) {
            throw exception(Objects.equals(indicatorLevel, FIRST_LEVEL)
                    ? INDICATOR_FIRST_LEVEL_WEIGHT_EXCEED
                    : INDICATOR_SECOND_LEVEL_WEIGHT_EXCEED);
        }
    }

    /**
     * 校验批量新增后的权重上限。
     */
    private void validateBatchCreateWeightLimit(CapacityIndicatorBatchCreateReqVO batchCreateReqVO) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        List<CapacityIndicatorDO> indicators = getScopedIndicators(
                batchCreateReqVO.getIndicatorLevel(), batchCreateReqVO.getParentIndicatorId());
        for (CapacityIndicatorDO indicator : indicators) {
            if (indicator.getWeight() != null) {
                totalWeight = totalWeight.add(indicator.getWeight());
            }
        }
        for (CapacityIndicatorBatchCreateReqVO.IndicatorItem item : batchCreateReqVO.getIndicators()) {
            totalWeight = totalWeight.add(ObjectUtil.defaultIfNull(item.getWeight(), BigDecimal.ZERO));
        }
        if (totalWeight.compareTo(MAX_WEIGHT) > 0) {
            throw exception(Objects.equals(batchCreateReqVO.getIndicatorLevel(), FIRST_LEVEL)
                    ? INDICATOR_FIRST_LEVEL_WEIGHT_EXCEED
                    : INDICATOR_SECOND_LEVEL_WEIGHT_EXCEED);
        }
    }

    /**
     * 获取当前校验作用域内的指标列表。
     */
    private List<CapacityIndicatorDO> getScopedIndicators(Integer indicatorLevel, Long parentIndicatorId) {
        return Objects.equals(indicatorLevel, FIRST_LEVEL)
                ? capacityIndicatorMapper.selectListByIndicatorLevel(FIRST_LEVEL)
                : capacityIndicatorMapper.selectListByParentIndicatorId(parentIndicatorId);
    }

    /**
     * 校验编辑时不可变字段（层级、父节点）未被修改。
     */
    private void validateUpdateImmutableFields(CapacityIndicatorDO oldIndicator, CapacityIndicatorSaveReqVO updateReqVO) {
        if (!Objects.equals(oldIndicator.getIndicatorLevel(), updateReqVO.getIndicatorLevel())) {
            throw exception(INDICATOR_LEVEL_CAN_NOT_CHANGE);
        }
        Long oldParentIndicatorId = normalizeParentIndicatorId(
                oldIndicator.getIndicatorLevel(), oldIndicator.getParentIndicatorId());
        Long newParentIndicatorId = normalizeParentIndicatorId(
                updateReqVO.getIndicatorLevel(), updateReqVO.getParentIndicatorId());
        if (!ObjectUtil.equal(oldParentIndicatorId, newParentIndicatorId)) {
            throw exception(INDICATOR_PARENT_CAN_NOT_CHANGE);
        }
    }

    /**
     * 统一父节点比较口径：一级指标视为 ROOT_PARENT_ID。
     */
    private Long normalizeParentIndicatorId(Integer indicatorLevel, Long parentIndicatorId) {
        return Objects.equals(indicatorLevel, FIRST_LEVEL) ? ROOT_PARENT_ID : parentIndicatorId;
    }

    /**
     * 校验指标存在并返回实体。
     */
    private CapacityIndicatorDO validateIndicatorExists(Long id) {
        CapacityIndicatorDO indicator = id == null ? null : capacityIndicatorMapper.selectById(id);
        if (indicator == null) {
            throw exception(INDICATOR_NOT_EXISTS);
        }
        return indicator;
    }

}
