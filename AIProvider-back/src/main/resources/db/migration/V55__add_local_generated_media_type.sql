ALTER TABLE c_LocalGeneratedImages
    ADD COLUMN MediaType VARCHAR(10) NOT NULL DEFAULT 'IMAGE' AFTER FileName,
    ADD COLUMN MimeType VARCHAR(100) NULL AFTER MediaType;

UPDATE c_LocalGeneratedImages
SET MediaType = 'VIDEO',
    MimeType = CASE
        WHEN LOWER(FileName) LIKE '%.mp4' THEN 'video/mp4'
        WHEN LOWER(FileName) LIKE '%.webm' THEN 'video/webm'
        WHEN LOWER(FileName) LIKE '%.mov' THEN 'video/quicktime'
        WHEN LOWER(FileName) LIKE '%.m4v' THEN 'video/x-m4v'
        ELSE MimeType
    END
WHERE LOWER(FileName) REGEXP '\\.(mp4|webm|mov|m4v)$';
