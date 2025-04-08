package com.example.photo_uploader.Service;

import com.example.photo_uploader.Repository.FileMetadataRepository;
import com.example.photo_uploader.entities.FileMetadata;
import io.minio.*;
import io.minio.errors.MinioException;
import io.minio.messages.Item;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MinioService {

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    @Value("${minio.bucket}")
    private String bucketName;


    public String uploadFile(MultipartFile file, String username) {
        try {
            // Проверяем, существует ли бакет
            boolean isExist = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucketName)
                            .build()
            );

            if (!isExist) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucketName)
                                .build()
                );
            }

            // Генерируем уникальное имя файла
            String fileName = UUID.randomUUID() + "-" + file.getOriginalFilename();

            // Загружаем файл в MinIO
            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(fileName)
                                .stream(inputStream, file.getSize(), -1)
                                .contentType(file.getContentType())
                                .build()
                );
            }

            // Создаём метаданные файла
            FileMetadata metadata = new FileMetadata();
            metadata.setFileName(fileName);
            metadata.setOriginalFileName(file.getOriginalFilename());
            metadata.setFileSize(file.getSize());
            metadata.setFileType(file.getContentType());
            metadata.setUploadTime(LocalDateTime.now());
            metadata.setUsername(username);  // Устанавливаем username

            fileMetadataRepository.save(metadata);

            return fileName;

        } catch (Exception e) {
            throw new RuntimeException("Ошибка при загрузке файла в MinIO", e);
        }
    }

    public InputStream downloadFile(String fileName) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileName)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при скачивании файла", e);
        }
    }

    public Iterable<Result<Item>> listFiles() {
        return minioClient.listObjects(ListObjectsArgs.builder().bucket(bucketName).build());
    }

    public List<String> getUserPhotos(String username) {
        List<String> photos = new ArrayList<>();
        try {
            // Получаем список файлов для конкретного пользователя из базы данных
            List<FileMetadata> userFiles = fileMetadataRepository.findByUsername(username);

            // Формируем список имен файлов
            for (FileMetadata metadata : userFiles) {
                photos.add(metadata.getFileName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return photos;
    }
}