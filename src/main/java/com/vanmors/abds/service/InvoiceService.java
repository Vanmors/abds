package com.vanmors.abds.service;

import com.vanmors.abds.exception.NotFoundException;
import com.vanmors.abds.model.InvoiceList;
import com.vanmors.abds.repository.InvoiceRepository;
import com.vanmors.abds.repository.RealtyRepository;

public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final RealtyRepository realtyRepository;

    public InvoiceService(final InvoiceRepository invoiceRepository, final RealtyRepository realtyRepository) {
        this.invoiceRepository = invoiceRepository;
        this.realtyRepository = realtyRepository;
    }

    public InvoiceList getPage(final long realtyId, final long limit, final long offset) {
        if (realtyRepository.findById(realtyId).isEmpty()) {
            throw new NotFoundException();
        }
        return new InvoiceList(
                invoiceRepository.findPage(realtyId, limit, offset),
                invoiceRepository.countByRealty(realtyId));
    }
}
