package com.vanmors.abds.service;

import com.vanmors.abds.exception.NotFoundException;
import com.vanmors.abds.model.User;
import com.vanmors.abds.model.UserPatch;
import com.vanmors.abds.repository.UserRepository;

public class UserService {

    private final UserRepository repository;

    public UserService(final UserRepository repository) {
        this.repository = repository;
    }

    public User get(final long id) {
        return repository.findById(id).orElseThrow(NotFoundException::new);
    }

    public void update(final long id, final UserPatch patch) {
        final User current = get(id);

        final String fio = patch.fio() != null ? patch.fio() : current.fio();
        String phone = current.phone();
        String phonePrev = current.phonePrev();
        if (patch.phone() != null && !patch.phone().equals(current.phone())) {
            phonePrev = current.phone();
            phone = patch.phone();
        }

        final User updated = new User(id, fio, phone, phonePrev);
        if (!updated.equals(current)) {
            repository.save(updated);
        }
    }
}
