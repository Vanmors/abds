package com.vanmors.abds.service;

import com.vanmors.abds.exception.NotFoundException;
import com.vanmors.abds.model.NewReading;
import com.vanmors.abds.model.ReadingList;
import com.vanmors.abds.model.Realty;
import com.vanmors.abds.repository.GaugeRepository;
import com.vanmors.abds.repository.ReadingRepository;
import com.vanmors.abds.repository.RealtyRepository;
import com.vanmors.abds.repository.TariffRepository;
import com.vanmors.abds.repository.UserRepository;

import java.time.LocalDate;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class RealtyService {

    private final RealtyRepository realtyRepository;
    private final ReadingRepository readingRepository;
    private final TariffRepository tariffRepository;
    private final GaugeRepository gaugeRepository;
    private final UserRepository userRepository;

    public RealtyService(final RealtyRepository realtyRepository,
                         final ReadingRepository readingRepository,
                         final TariffRepository tariffRepository,
                         final GaugeRepository gaugeRepository,
                         final UserRepository userRepository) {
        this.realtyRepository = realtyRepository;
        this.readingRepository = readingRepository;
        this.tariffRepository = tariffRepository;
        this.gaugeRepository = gaugeRepository;
        this.userRepository = userRepository;
    }

    public Realty get(final long id) {
        return realtyRepository.findById(id).orElseThrow(NotFoundException::new);
    }

    public ReadingList getReadings(final long realtyId, final LocalDate dateFrom, final LocalDate dateTo) {
        if (realtyRepository.findById(realtyId).isEmpty()) {
            throw new NotFoundException();
        }
        return ReadingList.of(readingRepository.findByPeriod(realtyId, dateFrom, dateTo));
    }

    public void addReading(final long realtyId, final NewReading reading) {
        if (!allExist(realtyId, reading)) {
            throw new NotFoundException();
        }
        readingRepository.insert(realtyId, reading);
    }

    private boolean allExist(final long realtyId, final NewReading reading) {
        try (final var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final Future<Boolean> realty = executor.submit(() -> realtyRepository.findById(realtyId).isPresent());
            final Future<Boolean> user = executor.submit(() -> userRepository.existsById(reading.userId()));
            final Future<Boolean> gauge = executor.submit(() -> gaugeRepository.existsById(reading.gaugeId()));
            final Future<Boolean> tariff = executor.submit(() -> tariffRepository.existsById(reading.tariffId()));
            return realty.get() && user.get() && gauge.get() && tariff.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Reading checks interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Reading checks failed", e.getCause());
        }
    }
}
