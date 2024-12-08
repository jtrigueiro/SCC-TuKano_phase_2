package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.errorOrVoid;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import redis.clients.jedis.Jedis;
import tukano.api.Blobs;
import tukano.api.Result;
import tukano.api.Short;
import tukano.api.Shorts;
import tukano.api.User;
import tukano.impl.cache.RedisCache;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoRestServer;
import utils.AzureProperties;
import utils.DB;
import utils.JSON;

public class JavaShorts implements Shorts {

	private static Logger Log = Logger.getLogger(JavaShorts.class.getName());

	private static Shorts instance;

	synchronized public static Shorts getInstance() {
		if (instance == null)
			instance = new JavaShorts();
		return instance;
	}

	private JavaShorts() {
	}

	@Override
	public Result<Short> createShort(String userId, String password) {
		Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

		return errorOrResult(okUser(userId, password), user -> {

			var shortId = format("%s+%s", userId, UUID.randomUUID());
																		
			var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, Blobs.NAME, shortId);
			var shrt = new Short(shortId, userId, blobUrl);

			return errorOrValue(DB.insertOne(shrt),
					s -> s.copyWithLikes_And_Token(0));
		});
	}

	@Override
	public Result<Short> getShort(String shortId) {
		Log.info(() -> format("getShort : shortId = %s\n", shortId));

		if (shortId == null)
			return error(BAD_REQUEST);

		if (AzureProperties.USE_REDIS) {
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				var cached = jedis.get(Shorts.NAME + ':' + shortId);

				if (cached != null)
					return Result.ok(JSON.decode(cached, Short.class));
			}
		}

		var query = format("SELECT VALUE count(1) FROM Likes l WHERE l.shortId = '%s'", shortId);
		var likes = DB.sql(query, Long.class);

		var result = errorOrValue(DB.getOne(shortId, Short.class),
				shrt -> shrt.copyWithLikes_And_Token(likes.get(0)));

		if (result.isOK() && AzureProperties.USE_REDIS) {
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				jedis.set(Shorts.NAME + ':' + shortId, JSON.encode(result.value()));
			}
		}

		return result;
	}

	@Override
	public Result<Void> deleteShort(String shortId, String password) {
		Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult(getShort(shortId), shrt -> {
			return errorOrResult(okUser(shrt.getOwnerId(), password), user -> {

				if (AzureProperties.USE_REDIS) {
					try (Jedis jedis = RedisCache.getCachePool().getResource()) {
						jedis.del(Shorts.NAME + ':' + shortId);
					}
				}

				DB.deleteOne(shrt);

				var query = format("SELECT Likes l WHERE l.shortId = '%s'", shortId);
				DB.sql(query, Likes.class);
				Log.info(() -> format("URL : BLOBURL = %s\n", shrt.getBlobUrl()));
				var a = JavaBlobs.getInstance().delete(shrt.getBlobUrl(), Token.get());
				Log.info(() -> format("ERRO : ERRODELETE = %s\n", a.error()));
				return Result.ok();
			});
		});
	}

	@Override
	public Result<List<String>> getShorts(String userId) {
		Log.info(() -> format("getShorts : userId = %s\n", userId));

		var query = format("SELECT VALUE s.id FROM Short s WHERE s.ownerId = '%s'", userId);
		return errorOrValue(okUser(userId), DB.sql(query, String.class));
	}

	@Override
	public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
		Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2,
				isFollowing, password));

		return errorOrResult(okUser(userId1, password), user -> {
			var f = new Following(userId1, userId2);

			return errorOrVoid(okUser(userId2), isFollowing ? DB.insertOne(f)
					: DB.deleteOne(f));
		});
	}

	@Override
	public Result<List<String>> followers(String userId, String password) {
		Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

		var query = format("SELECT VALUE f.follower FROM Following f WHERE f.followee = '%s'", userId);
		return errorOrValue(okUser(userId, password),
				DB.sql(query, String.class));
	}

	@Override
	public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked,
				password));

		return errorOrResult(getShort(shortId), shrt -> {
			var l = new Likes(userId, shortId, shrt.getOwnerId());

			return errorOrVoid(okUser(userId, password), isLiked ? DB.insertOne(l)
					: DB.deleteOne(l));
		});
	}

	@Override
	public Result<List<String>> likes(String shortId, String password) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult(getShort(shortId), shrt -> {

			var query = format("SELECT VALUE l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);

			return errorOrValue(okUser(shrt.getOwnerId(), password),
					DB.sql(query, String.class));
		});
	}

	public Result<List<String>> getFeed(String userId, String password) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

		final var QUERY_FMT = """
				SELECT s.shortId, s.timestamp FROM Short s WHERE	s.ownerId = '%s'				
				UNION			
				SELECT s.shortId, s.timestamp FROM Short s, Following f 
					WHERE 
						f.followee = s.ownerId AND f.follower = '%s' 
				ORDER BY s.timestamp DESC""";

		return errorOrValue( okUser( userId, password), DB.sql( format(QUERY_FMT, userId, userId), String.class));		
	}

	protected Result<User> okUser(String userId, String pwd) {
		return JavaUsers.getInstance().getUser(userId, pwd);
	}

	private Result<Void> okUser(String userId) {
		var res = okUser(userId, "");
		if (res.error() == FORBIDDEN)
			return ok();
		else
			return error(res.error());
	}

	@Override
	public Result<Void> deleteAllShorts(String userId, String password, String token) {
		Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));

		if( ! Token.isValid( token, userId ) )
			return error(FORBIDDEN);
		
		return DB.transaction( (hibernate) -> {
						
			//delete shorts
			var query1 = format("DELETE Short s WHERE s.ownerId = '%s'", userId);		
			hibernate.createQuery(query1, Short.class).executeUpdate();
			
			//delete follows
			var query2 = format("DELETE Following f WHERE f.follower = '%s' OR f.followee = '%s'", userId, userId);		
			hibernate.createQuery(query2, Following.class).executeUpdate();
			
			//delete likes
			var query3 = format("DELETE Likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);		
			hibernate.createQuery(query3, Likes.class).executeUpdate();
			
		});
	}
}