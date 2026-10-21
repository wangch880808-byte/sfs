-- Remove the retired class code without assuming whether an installation ever had it.
SET @drop_class_code_index = IF(
    EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'edu_class'
          AND index_name = 'uk_edu_class_class_code'
    ),
    'ALTER TABLE `edu_class` DROP INDEX `uk_edu_class_class_code`',
    'SELECT 1'
);
PREPARE stmt FROM @drop_class_code_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_class_code_column = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'edu_class'
          AND column_name = 'class_code'
    ),
    'ALTER TABLE `edu_class` DROP COLUMN `class_code`',
    'SELECT 1'
);
PREPARE stmt FROM @drop_class_code_column;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `system_user_profile`
    MODIFY COLUMN `grade_year` int DEFAULT NULL COMMENT '所属年级(入学年份)';
