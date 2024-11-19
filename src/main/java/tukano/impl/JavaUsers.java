package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import redis.clients.jedis.Jedis;
import tukano.api.Result;
import tukano.api.User;
import tukano.api.Users;
import tukano.impl.cache.RedisCache;
import tukano.impl.storage.CosmosDBLayer;
import utils.AzureProperties;
import utils.JSON;

public class JavaUsers implements Users {

	private static Logger Log = Logger.getLogger(JavaUsers.class.getName());

	private static Users instance;

	synchronized public static Users getInstance() {
		if (instance == null)
			instance = new JavaUsers();
		return instance;
	}

	private JavaUsers() {
	}

	@Override
	public Result<String> createUser(User user) {
		Log.info(() -> format("createUser : %s\n", user));

		if (badUserInfo(user))
			return error(BAD_REQUEST);

		var result = errorOrValue(CosmosDBLayer.getInstance(Users.NAME).insertOne(user),
				user.getId());

		if (AzureProperties.USE_REDIS && result.isOK())
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				jedis.set(Users.NAME + ':' + user.getId(), JSON.encode(user));
			}
		return result;
	}

	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info(() -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null)
			return error(BAD_REQUEST);

		if(AzureProperties.USE_REDIS) {
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				var cached = jedis.get(Users.NAME + ':' + userId);
				if (cached != null)
					return validatedUserOrError(Result.ok(JSON.decode(cached, User.class)), pwd);
			}
		}
		
		var result = validatedUserOrError(CosmosDBLayer.getInstance(Users.NAME).getOne(userId, User.class), pwd);

		if (AzureProperties.USE_REDIS && result.isOK()) {
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				jedis.set(Users.NAME + ':' + userId, JSON.encode(result.value()));
			}
		}

		return result;
	}

	@Override
	public Result<User> updateUser(String userId, String pwd, User other) {
		Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));

		if (badUpdateUserInfo(userId, pwd, other))
			return error(BAD_REQUEST);

		CosmosDBLayer db = CosmosDBLayer.getInstance(Users.NAME);
		Result<User> rUser = null;

		if(AzureProperties.USE_REDIS) {
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				var cached = jedis.get(Users.NAME + ':' + userId);

				if (cached != null)
					rUser = Result.ok(JSON.decode(cached, User.class));
			}
		}
			
		if(rUser == null)
			rUser = db.getOne(userId, User.class);

		if (validatedUserOrError(rUser, pwd).isOK()) {
			User user = rUser.value().updateFrom(other);

			if (db.updateOne(user.updateFrom(user)).isOK()) {
				rUser = db.getOne(userId, User.class);

				if (rUser.isOK() && AzureProperties.USE_REDIS) {
					try (Jedis jedis = RedisCache.getCachePool().getResource()) {
						jedis.set(Users.NAME + ':' + userId, JSON.encode(rUser.value()));
					}
				}
			}
		}

		return rUser;
	}

	@Override
	public Result<User> deleteUser(String userId, String pwd) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null)
			return error(BAD_REQUEST);

		Result<User> rUser = null;

		if(AzureProperties.USE_REDIS) {
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				var cached = jedis.get(Users.NAME + ':' + userId);

				if (cached != null)
					rUser = Result.ok(JSON.decode(cached, User.class));
			}
		}

		if(rUser == null)
			rUser = CosmosDBLayer.getInstance(Users.NAME).getOne(userId, User.class);
		
		return errorOrResult(validatedUserOrError(rUser, pwd), user -> {

			// Delete user shorts and related info asynchronously in a separate thread
			Executors.defaultThreadFactory().newThread(() -> {
				JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
				JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
			}).start();

			CosmosDBLayer.getInstance(Users.NAME).deleteOne(user);

			if (AzureProperties.USE_REDIS) {
				try (Jedis jedis = RedisCache.getCachePool().getResource()) {
					jedis.del(Users.NAME + ':' + userId);
				}
			}

			return Result.ok(user);
		});
	}

	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info(() -> format("searchUsers : patterns = %s\n", pattern));

		var query = format("SELECT * FROM User u WHERE UPPER(u.id) LIKE '%%%s%%'", pattern.toUpperCase());
		var hits = CosmosDBLayer.getInstance(Users.NAME).query(query, User.class).value()
				.stream()
				.map(User::copyWithoutPassword)
				.toList();

		return ok(hits);
	}

	private Result<User> validatedUserOrError(Result<User> res, String pwd) {
		if (res.isOK())
			return res.value().getPwd().equals(pwd) ? res : error(FORBIDDEN);
		else
			return res;
	}

	private boolean badUserInfo(User user) {
		return (user.userId() == null || user.pwd() == null || user.displayName() == null || user.email() == null);
	}

	private boolean badUpdateUserInfo(String userId, String pwd, User info) {
		return (userId == null || pwd == null || info.getId() != null && !userId.equals(info.getId()));
	}
}
