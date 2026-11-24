package com.vanmors.abds.service;

import com.vanmors.abds.exception.NotFoundException;
import com.vanmors.abds.model.Tariff;
import com.vanmors.abds.repository.TariffRepository;

import java.time.LocalDate;

public class TariffService {

    private final TariffRepository tariffRepository;

    public TariffService(final TariffRepository tariffRepository) {
        this.tariffRepository = tariffRepository;
    }

    public Tariff getValidAt(final LocalDate date) {
        return tariffRepository.findValidAt(date).orElseThrow(NotFoundException::new);
    }
}
