package com.embabel.example.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.assertj.core.util.IterableUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class InMemoryCrudRepositoryTest {
	private InMemoryCrudRepository<TestEntity> repository;

	private static record TestEntity(String id, String name, int value) {
		TestEntity(String name, int value) {
			this(null, name, value);
		}
	}

	@BeforeEach
	void setup() {
		repository = new InMemoryCrudRepository<>(entity -> entity.id(),
				(entity, id) -> new TestEntity(id, entity.name(), entity.value()));
	}

	@Test
	void testSaveNewEntity() {
		var entity = new TestEntity("test", 42);

		var saved = repository.save(entity);

		assertNotNull(saved.id());
		assertEquals("test", saved.name());
		assertEquals(42, saved.value());
		assertEquals(1, repository.count());
	}

	@Test
    void testSaveExistingEntity() {
        var entity = new TestEntity("existing-id", "test", 42);

        var saved = repository.save(entity);

        assertEquals("existing-id", saved.id());
        assertEquals("test", saved.name());
        assertEquals(42, saved.value());
        assertEquals(1, repository.count());
    }

    @Test
    void testSaveAll() {
        var entities = List.of(
            new TestEntity("first", 1),
            new TestEntity("second", 2)
        );

        var saved = repository.saveAll(entities);

        assertEquals(2, IterableUtil.sizeOf(saved));
        assertEquals(2, repository.count());
        saved.forEach (it -> assertNotNull(it.id()) );
    }

    @Test
    void testFindById() {
        var entity = new TestEntity("test", 42);
        var saved = repository.save(entity);

        var found = repository.findById(Objects.requireNonNull(saved.id()));

        assertTrue(found.isPresent());
        assertEquals(saved, found.get());
    }

    @Test
    void testFindByIdNotFound() {
        var found = repository.findById("non-existent");

        assertFalse(found.isPresent());
    }

    @Test
    void testExistsById() {
        var entity = new TestEntity("test", 42);
        var saved = repository.save(entity);

        assertTrue(repository.existsById(Objects.requireNonNull(saved.id())));
        assertFalse(repository.existsById("non-existent"));
    }

    @Test
    void testFindAll() {
        var entities = List.of(
            new TestEntity("first", 1),
            new TestEntity("second", 2),
            new TestEntity("third", 3)
        );
        repository.saveAll(entities);
        
        var all = IterableUtil.toCollection(repository.findAll());

        assertEquals(3, all.size());
        assertTrue(all.stream().filter(it -> it.name().equals("first")).findAny().isPresent());
        assertTrue(all.stream().filter(it -> it.name().equals("second")).findAny().isPresent());
        assertTrue(all.stream().filter(it -> it.name().equals("third")).findAny().isPresent());
    }

    @Test
    void testFindAllByIds() {
        var entities = List.of(
            new TestEntity("first", 1),
            new TestEntity("second", 2),
            new TestEntity("third", 3)
        );
        var saved = (List<TestEntity>)IterableUtil.toCollection(repository.saveAll(entities));
        var idsToFind = List.of(Objects.requireNonNull(saved.get(0).id()), Objects.requireNonNull(saved.get(2).id()));

        var found = IterableUtil.toCollection(repository.findAllById(idsToFind));

        assertEquals(2, found.size());
        assertTrue(found.stream().filter(it -> it.name().equals("first")).findAny().isPresent());
        assertTrue(found.stream().filter(it -> it.name().equals("third")).findAny().isPresent());
        assertFalse(found.stream().filter(it -> it.name().equals("second")).findAny().isPresent());
    }

    @Test
    void testFindAllByIdsWithNonExistentIds() {
        var entity = new TestEntity("test", 42);
        var saved = repository.save(entity);
        var idsToFind = List.of(Objects.requireNonNull(saved.id()), "non-existent");

        var found = (List<TestEntity>)IterableUtil.toCollection(repository.findAllById(idsToFind));

        assertEquals(1, found.size());
        assertEquals("test", found.get(0).name());
    }

    @Test
    void testCount() {
        assertEquals(0, repository.count());

        repository.save(new TestEntity("first", 1));
        assertEquals(1, repository.count());

        repository.save(new TestEntity("second", 2));
        assertEquals(2, repository.count());
    }

    @Test
    void testDeleteById() {
        var entity = new TestEntity("test", 42);
        var saved = repository.save(entity);
        assertEquals(1, repository.count());

        repository.deleteById(Objects.requireNonNull(saved.id()));

        assertEquals(0, repository.count());
        assertFalse(repository.existsById(Objects.requireNonNull(saved.id())));
    }

    @Test
    void testDeleteByIdNonExistent() {
        repository.deleteById("non-existent");

        assertEquals(0, repository.count());
    }

    @Test
    void testDelete() {
        var entity = new TestEntity("test", 42);
        var saved = repository.save(entity);
        assertEquals(1, repository.count());

        repository.delete(saved);

        assertEquals(0, repository.count());
        assertFalse(repository.existsById(Objects.requireNonNull(saved.id())));
    }

    @Test
    void testDeleteEntityWithoutId() {
        var entity = new TestEntity("test", 42);

        repository.delete(entity);

        assertEquals(0, repository.count());
    }

    @Test
    void testDeleteAllByIds() {
        var entities = List.of(
            new TestEntity("first", 1),
            new TestEntity("second", 2),
            new TestEntity("third", 3)
        );
        var saved = (List<TestEntity>)IterableUtil.toCollection(repository.saveAll(entities));
        assertEquals(3, repository.count());

        var idsToDelete = List.of(Objects.requireNonNull(saved.get(0).id()), Objects.requireNonNull(saved.get(2).id()));
        repository.deleteAllById(idsToDelete);

        assertEquals(1, repository.count());
        assertTrue(repository.existsById(Objects.requireNonNull(saved.get(1).id())));
        assertFalse(repository.existsById(Objects.requireNonNull(saved.get(0).id())));
        assertFalse(repository.existsById(Objects.requireNonNull(saved.get(2).id())));
    }

    @Test
    void testDeleteAllByEntities() {
        var entities = List.of(
            new TestEntity("first", 1),
            new TestEntity("second", 2),
            new TestEntity("third", 3)
        );
        var saved = (List<TestEntity>)IterableUtil.toCollection(repository.saveAll(entities));
        assertEquals(3, repository.count());

        var entitiesToDelete = List.of(saved.get(0), saved.get(2));
        repository.deleteAll(entitiesToDelete);

        assertEquals(1, repository.count());
        assertTrue(repository.existsById(Objects.requireNonNull(saved.get(1).id())));
        assertFalse(repository.existsById(Objects.requireNonNull(saved.get(0).id())));
        assertFalse(repository.existsById(Objects.requireNonNull(saved.get(2).id())));
    }

    @Test
    void testDeleteAll() {
        var entities = List.of(
            new TestEntity("first", 1),
            new TestEntity("second", 2),
            new TestEntity("third", 3)
        );
        repository.saveAll(entities);
        assertEquals(3, repository.count());

        repository.deleteAll();

        assertEquals(0, repository.count());
        assertTrue(IterableUtil.toCollection(repository.findAll()).isEmpty());
    }

    @Test
    void testUpdateExistingEntity() {
        var entity = new TestEntity("original", 42);
        var saved = repository.save(entity);

        var updated = new TestEntity(saved.id(), "updated", 100);
        var savedUpdated = repository.save(updated);

        assertEquals(saved.id, savedUpdated.id());
        assertEquals("updated", savedUpdated.name());
        assertEquals(100, savedUpdated.value());
        assertEquals(1, repository.count());
    }

    @Test
    void testConcurrentModification() {
    	List<TestEntity> entities = Stream.iterate(1, n -> n + 1).limit(100).map(it -> new TestEntity("entity" + it.intValue(), it)).toList();
    	
        entities.parallelStream().forEach (it -> repository.save(it) );

        assertEquals(100, repository.count());
        assertEquals(100, IterableUtil.toCollection(repository.findAll()).size());
    }

    @Test
    void testIdGenerationUniqueness() {
    	List<TestEntity> entities = Stream.iterate(1, n -> n + 1).limit(10).map(it -> new TestEntity("entity" + it.intValue(), it)).toList();
        var saved = IterableUtil.toCollection(repository.saveAll(entities));

        var ids = saved.stream().map (it -> it.id() ).collect(Collectors.toSet());
        assertEquals(10, ids.size());
    }
}
