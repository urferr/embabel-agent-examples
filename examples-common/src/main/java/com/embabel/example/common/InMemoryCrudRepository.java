package com.embabel.example.common;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.data.repository.CrudRepository;

public class InMemoryCrudRepository<T> implements CrudRepository<T, String> {
	private final ConcurrentHashMap<String, T> storage = new ConcurrentHashMap<>();

	private final Function<T, String> idGetter;
	private final BiFunction<T, String, T> idSetter;

	public InMemoryCrudRepository(Function<T, String> theIdGetter, BiFunction<T, String, T> theIdSetter) {
		idGetter = theIdGetter;
		idSetter = theIdSetter;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <S extends T> S save(S theEntity) {
		var saveEntity = theEntity;
		var aExistingId = idGetter.apply(theEntity);

		var aId = aExistingId != null ? aExistingId : UUID.randomUUID().toString();

		if (aExistingId == null) {
			saveEntity = (S) idSetter.apply(saveEntity, aId);
		}

		storage.put(aId, saveEntity);

		return saveEntity;
	}

	@Override
	public <S extends T> Iterable<S> saveAll(Iterable<S> theEntities) {
		var aEntitiesStream = StreamSupport.stream(theEntities.spliterator(), false);
		return aEntitiesStream.map(this::save).collect(Collectors.toList());
	}

	@Override
	public Optional<T> findById(String theId) {
		return Optional.ofNullable(storage.get(theId));
	}

	@Override
	public boolean existsById(String theId) {
		return storage.containsKey(theId);
	}

	@Override
	public Iterable<T> findAll() {
		return new ArrayList<>(storage.values());
	}

	@Override
	public Iterable<T> findAllById(Iterable<String> theIds) {
		var aIdsStream = StreamSupport.stream(theIds.spliterator(), false);
		return aIdsStream.map(storage::get).filter(theEntity -> theEntity != null).collect(Collectors.toList());
	}

	@Override
	public long count() {
		return storage.size();
	}

	@Override
	public void deleteById(String theId) {
		storage.remove(theId);

	}

	@Override
	public void delete(T theEntity) {
		var aId = idGetter.apply(theEntity);
		if (aId != null) {
			deleteById(aId);
		}
	}

	@Override
	public void deleteAllById(Iterable<? extends String> theIds) {
		theIds.forEach(this::deleteById);
	}

	@Override
	public void deleteAll(Iterable<? extends T> theEntities) {
		theEntities.forEach(this::delete);

	}

	@Override
	public void deleteAll() {
		storage.clear();
	}

}
