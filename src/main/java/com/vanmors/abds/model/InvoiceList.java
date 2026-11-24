package com.vanmors.abds.model;

import java.util.List;

public record InvoiceList(List<Invoice> invoices, long count) {
}
