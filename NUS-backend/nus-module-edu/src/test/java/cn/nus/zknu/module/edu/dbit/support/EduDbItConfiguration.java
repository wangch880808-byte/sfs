package cn.nus.zknu.module.edu.dbit.support;

import cn.hutool.extra.spring.SpringUtil;
import cn.nus.zknu.framework.datasource.config.NusDataSourceAutoConfiguration;
import cn.nus.zknu.framework.mybatis.config.NusMybatisAutoConfiguration;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApiImpl;
import cn.nus.zknu.module.capacity.api.indicator.CapacityIndicatorApiImpl;
import cn.nus.zknu.module.capacity.service.course.CapacityCourseMemberServiceImpl;
import cn.nus.zknu.module.edu.service.aireview.EduHomeworkAiReviewServiceImpl;
import cn.nus.zknu.module.edu.service.aireview.HomeworkAiReviewDispatcher;
import cn.nus.zknu.module.edu.service.aireview.config.EduAiReviewProperties;
import cn.nus.zknu.module.edu.service.homework.EduGradingPolicy;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkArchiveAccessService;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkExtensionServiceImpl;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkGradingActionService;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkRecordHistoryServiceImpl;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkRecordServiceImpl;
import cn.nus.zknu.module.edu.service.statistics.EduMetricPolicy;
import cn.nus.zknu.module.edu.service.student.StudentLevelUnlockService;
import cn.nus.zknu.module.infra.api.config.ConfigApiImpl;
import cn.nus.zknu.module.infra.service.config.ConfigCacheService;
import cn.nus.zknu.module.infra.service.config.ConfigServiceImpl;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import javax.sql.DataSource;

/**
 * db 集成测试的最小 Spring 上下文：真实的数据源、事务、MyBatis-Plus（含全部 Mapper 与 XML），
 * 加上被测的 Service；其余依赖（AI 模型调用、通知发送、文件存储、用户服务等）在 {@link EduDbIntegrationTest} 里 mock。
 * <p>
 * 不做组件扫描、不加载 Web/Redis/Quartz/安全等自动配置，启动只需几秒。
 */
@SpringBootConfiguration
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        TransactionAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class,
        NusDataSourceAutoConfiguration.class,
        NusMybatisAutoConfiguration.class,
        MybatisPlusAutoConfiguration.class,
        MybatisPlusJoinAutoConfiguration.class,
})
@Import({
        // 最小上下文未启用完整自动配置；insertBatch 的 JdbcUtils 依赖 Hutool 的静态 Spring bean 查找。
        SpringUtil.class,
        // 提交、批改、打回、撤销
        EduHomeworkRecordServiceImpl.class,
        EduHomeworkGradingActionService.class,
        EduGradingPolicy.class,
        EduHomeworkRecordHistoryServiceImpl.class,
        EduHomeworkArchiveAccessService.class,
        EduHomeworkExtensionServiceImpl.class,
        // AI 初评排队与派发（模型调用另行 mock）
        EduHomeworkAiReviewServiceImpl.class,
        HomeworkAiReviewDispatcher.class,
        EduAiReviewProperties.class,
        // 统计口径、等级闯关
        EduMetricPolicy.class,
        StudentLevelUnlockService.class,
        // 课程、课程团队与指标走真实 capacity API（Mapper 已由 MyBatis 扫描）
        CapacityCourseApiImpl.class,
        CapacityCourseMemberServiceImpl.class,
        CapacityIndicatorApiImpl.class,
        // 读真实 infra_config
        ConfigApiImpl.class,
        ConfigServiceImpl.class,
        // 参数读取经 ConfigCacheService；最小上下文未开启缓存，等同直接查库
        ConfigCacheService.class,
})
public class EduDbItConfiguration {

    /** 代替 AI 初评线程池：只记录派发，不调用模型。 */
    @Bean(name = "eduAiReviewTaskExecutor")
    public RecordingExecutor eduAiReviewTaskExecutor(DataSource dataSource) {
        return new RecordingExecutor(dataSource);
    }
}
