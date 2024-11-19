package tukano.impl.cache;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import utils.AzureKeys;
import utils.AzureProperties;

public class RedisCache {
	private static final int REDIS_PORT = 6380;
	private static final int REDIS_TIMEOUT = 1000;
	private static final boolean Redis_USE_TLS = true;

	private static JedisPool instance;

	public synchronized static JedisPool getCachePool() {
		if (instance != null)
			return instance;

		var poolConfig = new JedisPoolConfig();
		poolConfig.setMaxTotal(128);
		poolConfig.setMaxIdle(128);
		poolConfig.setMinIdle(16);
		poolConfig.setTestOnBorrow(true);
		poolConfig.setTestOnReturn(true);
		poolConfig.setTestWhileIdle(true);
		poolConfig.setNumTestsPerEvictionRun(3);
		poolConfig.setBlockWhenExhausted(true);
		instance = new JedisPool(poolConfig, "redis5811956837northeurope.redis.cache.windows.net", REDIS_PORT,
				REDIS_TIMEOUT,
				"2sRvLykf0eSd3Jidvh4Mfwz8YkNzeeswNAzCaArhTFs=", Redis_USE_TLS);
		return instance;
	}
}
