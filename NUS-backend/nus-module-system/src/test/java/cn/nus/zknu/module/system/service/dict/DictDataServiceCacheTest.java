package cn.nus.zknu.module.system.service.dict;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.module.system.cache.RedisCacheTestSupport;
import cn.nus.zknu.module.system.controller.admin.dict.DictDataController;
import cn.nus.zknu.module.system.controller.admin.dict.vo.data.DictDataPageReqVO;
import cn.nus.zknu.module.system.controller.admin.dict.vo.data.DictDataSaveReqVO;
import cn.nus.zknu.module.system.controller.admin.dict.vo.type.DictTypeSaveReqVO;
import cn.nus.zknu.module.system.dal.dataobject.dict.DictDataDO;
import cn.nus.zknu.module.system.dal.dataobject.dict.DictTypeDO;
import cn.nus.zknu.module.system.dal.mysql.dict.DictDataMapper;
import cn.nus.zknu.module.system.dal.mysql.dict.DictTypeMapper;
import cn.nus.zknu.module.system.dal.redis.RedisKeyConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DictDataServiceCacheTest extends RedisCacheTestSupport {

    private DictDataService service;
    private DictTypeService types;
    private DictDataMapper mapper;
    private DictTypeMapper typeMapper;
    private final Map<Long, DictDataDO> rows = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        startContext(Beans.class, DictDataServiceImpl.class, DictTypeServiceImpl.class,
                DictDataController.class);
        service = context.getBean(DictDataService.class);
        types = context.getBean(DictTypeService.class);
        mapper = context.getBean(DictDataMapper.class);
        typeMapper = context.getBean(DictTypeMapper.class);
        when(mapper.selectListByStatusAndDictType(any(), any())).thenAnswer(i -> new ArrayList<>(rows.values()
                .stream().filter(row -> i.getArgument(0) == null || row.getStatus().equals(i.getArgument(0)))
                .filter(row -> i.getArgument(1) == null || row.getDictType().equals(i.getArgument(1))).toList()));
        when(mapper.selectList(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class), anyString()))
                .thenAnswer(i -> new ArrayList<>(rows.values().stream()
                        .filter(row -> row.getDictType().equals(i.getArgument(1))).toList()));
        when(mapper.selectPage(any(DictDataPageReqVO.class)))
                .thenAnswer(i -> new PageResult<>(new ArrayList<>(rows.values()), (long) rows.size()));
        when(mapper.selectById(anyLong())).thenAnswer(i -> rows.get(i.getArgument(0)));
        when(mapper.insert(any(DictDataDO.class))).thenAnswer(i -> {
            DictDataDO row = i.getArgument(0);
            row.setId(1L + rows.size());
            rows.put(row.getId(), row);
            return 1;
        });
        when(mapper.updateById(any(DictDataDO.class))).thenAnswer(i -> {
            DictDataDO row = i.getArgument(0);
            rows.put(row.getId(), row);
            return 1;
        });
        when(mapper.deleteById(anyLong())).thenAnswer(i -> {
            rows.remove(i.getArgument(0));
            return 1;
        });
        when(mapper.deleteByIds(anyCollection())).thenAnswer(i -> {
            rows.clear();
            return 1;
        });
        when(typeMapper.selectByType(anyString())).thenAnswer(i -> type(1L, i.getArgument(0)));
        when(typeMapper.selectById(anyLong())).thenReturn(type(1L, "empty"));
        when(typeMapper.selectByIds(anyCollection())).thenReturn(List.of(type(1L, "empty")));
    }

    @Test
    void simpleListCachesEnabledItemsWithOriginalTypeAndSortOrderAndOneHourTtl() {
        rows.put(1L, row(1L, "b", 1, 0));
        rows.put(2L, row(2L, "a", 2, 0));
        rows.put(3L, row(3L, "a", 1, 0));
        rows.put(4L, row(4L, "a", 0, 1));
        DictDataController controller = context.getBean(DictDataController.class);
        var first = controller.getSimpleDictDataList().getData();
        var second = controller.getSimpleDictDataList().getData();
        assertEquals(List.of("a1", "a2", "b1"), second.stream().map(o -> o.getLabel()).toList());
        assertEquals(first, second);
        verify(mapper, times(1)).selectListByStatusAndDictType(0, null);
        assertTtl(RedisKeyConstants.DICT_DATA, Duration.ofHours(1));
        advance(Duration.ofHours(2));
        controller.getSimpleDictDataList();
        verify(mapper, times(2)).selectListByStatusAndDictType(0, null);
    }

    @Test
    void byTypeCacheIncludesDisabledItemsAndPageKeyUsesAllFilters() {
        rows.put(1L, row(1L, "a", 2, 0));
        rows.put(2L, row(2L, "a", 1, 1));
        var first = service.getDictDataListByDictType("a");
        assertEquals(List.of(2L, 1L), first.stream().map(DictDataDO::getId).toList());
        assertEquals(first, service.getDictDataListByDictType("a"));
        verify(mapper, times(1)).selectList(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class), eq("a"));
        DictDataPageReqVO request = new DictDataPageReqVO();
        var page = service.getDictDataPage(request);
        assertEquals(page, service.getDictDataPage(new DictDataPageReqVO()));
        request.setPageNo(2);
        service.getDictDataPage(request);
        request.setLabel("过滤");
        service.getDictDataPage(request);
        request.setLabel("a, b");
        request.setDictType("c");
        service.getDictDataPage(request);
        request.setLabel("a");
        request.setDictType("b, c");
        service.getDictDataPage(request);
        verify(mapper, times(5)).selectPage(any(DictDataPageReqVO.class));
    }

    @Test
    void dataCreateUpdateDeleteAndBatchDeleteClearEveryQueryShape() {
        assertTrue(service.getDictDataList(0, null).isEmpty());
        assertTrue(service.getDictDataListByDictType("a").isEmpty());
        assertEquals(0L, service.getDictDataPage(new DictDataPageReqVO()).getTotal());
        Long id = service.createDictData(request(null, "初始标签"));
        assertEquals("初始标签", service.getDictDataList(0, null).get(0).getLabel());
        assertEquals("初始标签", service.getDictDataListByDictType("a").get(0).getLabel());
        assertEquals(1L, service.getDictDataPage(new DictDataPageReqVO()).getTotal());
        service.updateDictData(request(id, "新标签"));
        assertEquals("新标签", service.getDictDataList(0, null).get(0).getLabel());
        service.deleteDictData(id);
        assertTrue(service.getDictDataList(0, null).isEmpty());
        Long another = service.createDictData(request(null, "批量标签"));
        assertEquals("批量标签", service.getDictDataList(0, null).get(0).getLabel());
        service.deleteDictDataList(List.of(another));
        assertTrue(service.getDictDataList(0, null).isEmpty());
        verify(mapper, times(6)).selectListByStatusAndDictType(0, null);
    }

    @Test
    void typeCreateUpdateDeleteAndBatchDeleteClearDataCacheThroughAnnotations() {
        rows.put(1L, row(1L, "a", 1, 0));
        service.getDictDataList(0, null);
        // 类型校验仍走实时 mapper；空类型的变更同样要清除全局 simple-list 缓存。
        when(typeMapper.selectByType("empty")).thenReturn(null);
        DictTypeSaveReqVO request = new DictTypeSaveReqVO();
        request.setType("empty");
        request.setName("空类型");
        request.setStatus(0);
        types.createDictType(request);
        service.getDictDataList(0, null);
        request.setId(1L);
        types.updateDictType(request);
        service.getDictDataList(0, null);
        types.deleteDictType(1L);
        service.getDictDataList(0, null);
        types.deleteDictTypeList(List.of(1L));
        service.getDictDataList(0, null);
        verify(mapper, times(5)).selectListByStatusAndDictType(0, null);
    }

    @Test
    void failedDataAndTypeWritesKeepCachedResultAndValidation() {
        rows.put(1L, row(1L, "a", 1, 0));
        var before = service.getDictDataList(0, null);
        assertThrows(RuntimeException.class, () -> service.deleteDictData(99L));
        when(mapper.selectCountByDictType("empty")).thenReturn(1L);
        assertThrows(RuntimeException.class, () -> types.deleteDictType(1L));
        assertEquals(before, service.getDictDataList(0, null));
        verify(mapper, times(1)).selectListByStatusAndDictType(0, null);
    }

    private DictDataDO row(Long id, String type, int sort, int status) {
        DictDataDO row = new DictDataDO();
        row.setId(id);
        row.setDictType(type);
        row.setSort(sort);
        row.setStatus(status);
        row.setLabel(type + sort);
        row.setValue(type + sort);
        return row;
    }

    private DictTypeDO type(Long id, String name) {
        DictTypeDO type = new DictTypeDO();
        type.setId(id);
        type.setType(name);
        type.setStatus(0);
        return type;
    }

    private DictDataSaveReqVO request(Long id, String label) {
        DictDataSaveReqVO request = new DictDataSaveReqVO();
        request.setId(id);
        request.setDictType("a");
        request.setValue("value");
        request.setLabel(label);
        request.setStatus(0);
        request.setSort(1);
        return request;
    }

    @Configuration(proxyBeanMethods = false)
    static class Beans {
        @Bean
        DictDataMapper dictDataMapper() {
            return mock(DictDataMapper.class);
        }
        @Bean
        DictTypeMapper dictTypeMapper() {
            return mock(DictTypeMapper.class);
        }
    }
}
