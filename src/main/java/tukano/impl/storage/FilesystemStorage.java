package tukano.impl.storage;

import static tukano.api.Result.error;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.CONFLICT;
import static tukano.api.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.Result.ErrorCode.NOT_FOUND;

import java.util.Arrays;
import java.util.function.Consumer;

import tukano.api.Result;
import utils.Hash;
import java.nio.file.*;

public class FilesystemStorage implements BlobStorage {
    private static final String BLOBS_DIRECTORY = "/app/blobs"; // Mount point of the PV in the pod

    public FilesystemStorage() {
        try {
            // Ensure the directory exists
            Files.createDirectories(Paths.get(BLOBS_DIRECTORY));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Result<Void> write(String path, byte[] bytes) {
        if (path == null) {
            return error(BAD_REQUEST);
        }

        try {
            Path filePath = Paths.get(BLOBS_DIRECTORY, path);

            // Check if file exists and has the same content
            if (Files.exists(filePath)) {
                byte[] existingBytes = Files.readAllBytes(filePath);
                if (Arrays.equals(Hash.sha256(bytes), Hash.sha256(existingBytes))) {
                    return ok();
                } else {
                    return error(CONFLICT);
                }
            }

            // Write bytes to file
            Files.write(filePath, bytes, StandardOpenOption.CREATE_NEW);
        } catch (Exception e) {
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }

        return ok();
    }

    @Override
    public Result<byte[]> read(String path) {
        if (path == null) {
            return error(BAD_REQUEST);
        }

        try {
            Path filePath = Paths.get(BLOBS_DIRECTORY, path);

            // Check if file exists
            if (!Files.exists(filePath)) {
                return error(NOT_FOUND);
            }

            // Read bytes from file
            return ok(Files.readAllBytes(filePath));
        } catch (Exception e) {
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> read(String path, Consumer<byte[]> sink) {
        if (path == null) {
            return error(BAD_REQUEST);
        }

        try {
            Path filePath = Paths.get(BLOBS_DIRECTORY, path);

            // Check if file exists
            if (!Files.exists(filePath)) {
                return error(NOT_FOUND);
            }

            // Read bytes and send them to sink
            sink.accept(Files.readAllBytes(filePath));
        } catch (Exception e) {
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }

        return ok();
    }

    @Override
    public Result<Void> delete(String path) {
        if (path == null) {
            return error(BAD_REQUEST);
        }

        try {
            Path filePath = Paths.get(BLOBS_DIRECTORY, path);

            // Check if file exists
            if (!Files.exists(filePath)) {
                return error(NOT_FOUND);
            }

            // Delete file
            Files.delete(filePath);
        } catch (Exception e) {
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }

        return ok();
    }
}
