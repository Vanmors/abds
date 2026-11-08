package com.vanmors.abds.service;

import com.vanmors.abds.exception.NotFoundException;
import com.vanmors.abds.model.GaugeBinding;
import com.vanmors.abds.repository.GaugeBindingsRepository;
import com.vanmors.abds.repository.GaugeRepository;
import com.vanmors.abds.repository.RealtyRepository;

import java.util.Optional;

public class GaugeBindingsService {

    private static final byte INSTALL = 1;
    private static final byte CANCEL = -1;
    private static final int FIRST_VERSION = 1;

    private final GaugeBindingsRepository gaugeBindingsRepository;
    private final RealtyRepository realtyRepository;
    private final GaugeRepository gaugeRepository;

    public GaugeBindingsService(final GaugeBindingsRepository gaugeBindingsRepository,
                                final RealtyRepository realtyRepository,
                                final GaugeRepository gaugeRepository) {
        this.gaugeBindingsRepository = gaugeBindingsRepository;
        this.realtyRepository = realtyRepository;
        this.gaugeRepository = gaugeRepository;
    }

    public void bind(final long realtyId, final long gaugeId) {
        if (realtyRepository.findById(realtyId).isEmpty() || !gaugeRepository.existsById(gaugeId)) {
            throw new NotFoundException();
        }

        final Optional<GaugeBinding> active = gaugeBindingsRepository.findActive(realtyId);
        if (active.isEmpty()) {
            gaugeBindingsRepository.insert(new GaugeBinding(gaugeId, realtyId, INSTALL, FIRST_VERSION));
            return;
        }

        final GaugeBinding current = active.get();
        gaugeBindingsRepository.insertPair(
                new GaugeBinding(current.gauge_id(), realtyId, CANCEL, current.version()),
                new GaugeBinding(gaugeId, realtyId, INSTALL, current.version() + 1));
    }
}
