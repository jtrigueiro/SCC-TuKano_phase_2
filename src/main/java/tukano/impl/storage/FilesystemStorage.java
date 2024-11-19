package tukano.impl.storage;

import static tukano.api.Result.error;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.CONFLICT;
import static tukano.api.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.Result.ErrorCode.NOT_FOUND;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;

import java.util.Arrays;
import java.util.function.Consumer;

import tukano.api.Result;
import utils.AzureKeys;
import utils.AzureProperties;
import utils.Hash;

public class FilesystemStorage implements BlobStorage {
	private static final String BLOBS_CONTAINER_NAME = "shorts";

	private BlobContainerClient containerClient;

	public FilesystemStorage() {

		try {
			// Get container client
			containerClient = new BlobContainerClientBuilder()
					.connectionString(
							"DefaultEndpointsProtocol=https;AccountName=sto5811956837northeurope;AccountKey=EIBHyVGN5RN+9feYthEtMaKPukVqnLByb1lPWn8tCoKKfP4WYN4HoGj+KMDzxpjO61GNRHgyi463+AStHxJhDg==;EndpointSuffix=core.windows.net")
					.containerName(BLOBS_CONTAINER_NAME)
					.buildClient();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public Result<Void> write(String path, byte[] bytes) {
		if (path == null)
			return error(BAD_REQUEST);

		try {
			// Get client to blob
			BlobClient blob = containerClient.getBlobClient(path);

			// Check if blob exists and has the same content
			if (blob.exists()) {
				if (Arrays.equals(Hash.sha256(bytes), Hash.sha256(blob.downloadContent().toBytes())))
					return ok();
				else
					return error(CONFLICT);
			}

			// Upload contents from BinaryData
			blob.upload(BinaryData.fromBytes(bytes));
		} catch (Exception e) {
			e.printStackTrace();
			return error(INTERNAL_ERROR);
		}

		return ok();
	}

	@Override
	public Result<byte[]> read(String path) {
		if (path == null)
			return error(BAD_REQUEST);

		try {
			// Get client to blob
			BlobClient blob = containerClient.getBlobClient(path);

			// Check if blob exists
			if (!blob.exists())
				return error(NOT_FOUND);

			return ok(blob.downloadContent().toBytes());
		} catch (Exception e) {
			e.printStackTrace();
			return error(INTERNAL_ERROR);
		}
	}

	@Override
	public Result<Void> read(String path, Consumer<byte[]> sink) {
		if (path == null)
			return error(BAD_REQUEST);

		try {
			// Get client to blob
			BlobClient blob = containerClient.getBlobClient(path);

			// Check if blob exists
			if (!blob.exists())
				return error(NOT_FOUND);

			// Download contents to sink
			sink.accept(blob.downloadContent().toBytes());
		} catch (Exception e) {
			e.printStackTrace();
			return error(INTERNAL_ERROR);
		}

		return ok();
	}

	@Override
	public Result<Void> delete(String path) {
		if (path == null)
			return error(BAD_REQUEST);

		try {
			// Get client to blob
			BlobClient blob = containerClient.getBlobClient(path);

			// Check if blob exists
			if (!blob.exists())
				return error(NOT_FOUND);

			// Delete blob
			blob.delete();
		} catch (Exception e) {
			e.printStackTrace();
			return error(INTERNAL_ERROR);
		}

		return ok();
	}

}
