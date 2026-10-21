package cn.nus.zknu.module.edu.service.dataexchange.student;

import lombok.Data;

@Data
public class StudentRelationImportRow {

    private String username;
    private Long classId;
    private String remark;

}
