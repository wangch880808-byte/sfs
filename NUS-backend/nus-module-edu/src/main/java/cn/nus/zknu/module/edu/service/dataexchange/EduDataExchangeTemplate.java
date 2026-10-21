package cn.nus.zknu.module.edu.service.dataexchange;

public interface EduDataExchangeTemplate {

    String getBizType();

    String getTemplateVersion();

    String getFileName();

    void validatePermission();

    byte[] generate();

}
