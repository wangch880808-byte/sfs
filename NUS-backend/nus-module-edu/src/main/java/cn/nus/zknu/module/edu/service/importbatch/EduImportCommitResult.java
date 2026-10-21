package cn.nus.zknu.module.edu.service.importbatch;

public record EduImportCommitResult(int insertedCount, int updatedCount, int skippedCount) {
}
