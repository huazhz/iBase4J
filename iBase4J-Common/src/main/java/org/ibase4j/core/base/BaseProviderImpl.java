package org.ibase4j.core.base;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.PropertyUtils;
import org.ibase4j.core.util.DataUtil;
import org.ibase4j.core.util.InstanceUtil;
import org.ibase4j.core.util.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.ContextLoader;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;

/**
 * 业务逻辑层基类
 * 
 * @author ShenHuaJie
 * @version 2016年5月20日 下午3:19:19
 */
public abstract class BaseProviderImpl<T extends BaseModel> {
	@Autowired
	private KeyGenerator keyGenerator;
	@Autowired
	private RedisSerializer<String> keySerializer;
	@Autowired
	private RedisSerializer<Object> valueSerializer;

	/** 启动分页查询 */
	protected void startPage(Map<String, Object> params) {
		if (DataUtil.isEmpty(params.get("pageNum"))) {
			params.put("pageNum", 1);
		}
		if (DataUtil.isEmpty(params.get("pageSize"))) {
			params.put("pageSize", 10);
		}
		if (DataUtil.isEmpty(params.get("orderBy"))) {
			params.put("orderBy", "id_ desc");
		}
		PageHelper.startPage(params);
	}

	@SuppressWarnings("unchecked")
	private BaseProviderImpl<T> getService() {
		return ContextLoader.getCurrentWebApplicationContext().getBean(getClass());
	}

	/** 根据Id查询(默认类型T) */
	public PageInfo<T> getPage(Page<Integer> ids) {
		Page<T> page = new Page<T>(ids.getPageNum(), ids.getPageSize());
		page.setTotal(ids.getTotal());
		if (ids != null) {
			page.clear();
			BaseProviderImpl<T> provider = getService();
			for (Integer id : ids) {
				page.add(provider.queryById(id));
			}
		}
		return new PageInfo<T>(page);
	}

	/** 根据Id查询(cls返回类型Class) */
	public <K> PageInfo<K> getPage(Page<Integer> ids, Class<K> cls) {
		Page<K> page = new Page<K>(ids.getPageNum(), ids.getPageSize());
		page.setTotal(ids.getTotal());
		if (ids != null) {
			page.clear();
			BaseProviderImpl<T> provider = getService();
			for (Integer id : ids) {
				T t = provider.queryById(id);
				K k = null;
				try {
					k = cls.newInstance();
				} catch (Exception e1) {
				}
				if (k != null) {
					try {
						PropertyUtils.copyProperties(k, t);
					} catch (Exception e) {
					}
					page.add(k);
				}
			}
		}
		return new PageInfo<K>(page);
	}

	/** 根据Id查询(默认类型T) */
	public List<T> getList(List<Integer> ids) {
		List<T> list = InstanceUtil.newArrayList();
		if (ids != null) {
			for (Integer id : ids) {
				list.add(getService().queryById(id));
			}
		}
		return list;
	}

	/** 根据Id查询(cls返回类型Class) */
	public <K> List<K> getList(List<Integer> ids, Class<K> cls) {
		List<K> list = InstanceUtil.newArrayList();
		if (ids != null) {
			for (Integer id : ids) {
				T t = getService().queryById(id);
				K k = null;
				try {
					k = cls.newInstance();
				} catch (Exception e1) {
				}
				if (k != null) {
					try {
						PropertyUtils.copyProperties(k, t);
					} catch (Exception e) {
					}
					list.add(k);
				}
			}
		}
		return list;
	}

	/** 获取缓存key */
	private byte[] getCacheKey(Integer id) {
		String key = keyGenerator.generate(this, getClass().getMethods()[0], id).toString();
		return keySerializer.serialize(key);
	}

	/** 更新缓存 */
	private void setCache(byte[] key, T record) {
		RedisUtil.set(key, valueSerializer.serialize(record));
	}

	/** 获取缓存 */
	@SuppressWarnings("unchecked")
	private T getCache(byte[] key) {
		byte[] value = RedisUtil.get(key);
		if (value != null) {
			return (T) valueSerializer.deserialize(value);
		}
		return null;
	}

	@Transactional
	public void delete(Integer id, Integer userId) {
		try {
			T record = queryById(id);
			record.setEnable(0);
			record.setUpdateTime(new Date());
			record.setUpdateBy(userId);
			getMapper().updateByPrimaryKey(record);
			setCache(getCacheKey(id), record);
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	@Transactional
	public T update(T record) {
		try {
			record.setEnable(0);
			record.setUpdateTime(new Date());
			if (record.getId() == null) {
				record.setCreateTime(new Date());
				getMapper().insert(record);
			} else {
				getMapper().updateByPrimaryKey(record);
			}
			setCache(getCacheKey(record.getId()), record);
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage(), e);
		}
		return record;
	}

	@Transactional
	public T queryById(Integer id) {
		try {
			byte[] key = getCacheKey(id);
			T record = getCache(key);
			if (record == null) {
				record = getMapper().selectByPrimaryKey(id);
				setCache(key, record);
			}
			return record;
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	protected abstract BaseMapper<T> getMapper();
}