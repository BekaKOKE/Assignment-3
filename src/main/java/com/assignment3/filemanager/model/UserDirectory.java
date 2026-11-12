package com.assignment3.filemanager.model;

import java.util.List;
import java.util.Optional;

public final class UserDirectory {

    private final List<User> users;

    private UserDirectory(List<User> users) {
        this.users = List.copyOf(users);
    }

    public static UserDirectory predefined() {
        return new UserDirectory(List.of(
                new User("beksultan", "Beksultan"),
                new User("ruslan", "Ruslan"),
                new User("kuanysh", "Kuanysh")));
    }

    public List<User> all() {
        return users;
    }

    public Optional<User> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String wanted = id.trim();
        return users.stream().filter(user -> user.id().equalsIgnoreCase(wanted)).findFirst();
    }

    public boolean contains(User user) {
        return user != null && users.stream().anyMatch(known -> known.id().equals(user.id()));
    }
}
