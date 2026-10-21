package cn.nus.zknu.module.edu.controller.admin.classstudent;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EduClassStudentControllerContractTest {

    @Test
    void batchResetPasswordEndpointIsRemoved() {
        assertThat(Arrays.stream(EduClassStudentController.class.getDeclaredMethods())
                .map(method -> AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class))
                .filter(Objects::nonNull)
                .flatMap(mapping -> Arrays.stream(mapping.path())))
                .doesNotContain("/reset-password-batch");
        assertThatThrownBy(() -> Class.forName(
                "cn.nus.zknu.module.edu.controller.admin.classstudent.vo.ResetPasswordBatchReqVO"))
                .isInstanceOf(ClassNotFoundException.class);
    }

}
