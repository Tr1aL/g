package org.tr1al.gainium.mock;

import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;
import org.tr1al.gainium.entity.Setting;
import org.tr1al.gainium.repository.SettingRepository;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class SettingRepositoryMock implements SettingRepository {
    @Override
    public void flush() {

    }

    @Override
    public <S extends Setting> S saveAndFlush(S entity) {
        return null;
    }

    @Override
    public <S extends Setting> List<S> saveAllAndFlush(Iterable<S> entities) {
        return null;
    }

    @Override
    public void deleteAllInBatch(Iterable<Setting> entities) {

    }

    @Override
    public void deleteAllByIdInBatch(Iterable<Integer> integers) {

    }

    @Override
    public void deleteAllInBatch() {

    }

    @Override
    public Setting getOne(Integer integer) {
        return null;
    }

    @Override
    public Setting getById(Integer integer) {
        return null;
    }

    @Override
    public Setting getReferenceById(Integer integer) {
        return null;
    }

    @Override
    public <S extends Setting> Optional<S> findOne(Example<S> example) {
        return Optional.empty();
    }

    @Override
    public <S extends Setting> List<S> findAll(Example<S> example) {
        return null;
    }

    @Override
    public <S extends Setting> List<S> findAll(Example<S> example, Sort sort) {
        return null;
    }

    @Override
    public <S extends Setting> Page<S> findAll(Example<S> example, Pageable pageable) {
        return null;
    }

    @Override
    public <S extends Setting> long count(Example<S> example) {
        return 0;
    }

    @Override
    public <S extends Setting> boolean exists(Example<S> example) {
        return false;
    }

    @Override
    public <S extends Setting, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
        return null;
    }

    @Override
    public <S extends Setting> S save(S entity) {
        return null;
    }

    @Override
    public <S extends Setting> List<S> saveAll(Iterable<S> entities) {
        return null;
    }

    @Override
    public Optional<Setting> findById(Integer integer) {
        Setting setting = new Setting();
        setting.setPaperContext(true);
        return Optional.of(setting);
    }

    @Override
    public boolean existsById(Integer integer) {
        return false;
    }

    @Override
    public List<Setting> findAll() {
        return null;
    }

    @Override
    public List<Setting> findAllById(Iterable<Integer> integers) {
        return null;
    }

    @Override
    public long count() {
        return 0;
    }

    @Override
    public void deleteById(Integer integer) {

    }

    @Override
    public void delete(Setting entity) {

    }

    @Override
    public void deleteAllById(Iterable<? extends Integer> integers) {

    }

    @Override
    public void deleteAll(Iterable<? extends Setting> entities) {

    }

    @Override
    public void deleteAll() {

    }

    @Override
    public List<Setting> findAll(Sort sort) {
        return null;
    }

    @Override
    public Page<Setting> findAll(Pageable pageable) {
        return null;
    }
}
