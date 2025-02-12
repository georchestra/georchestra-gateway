package org.georchestra.gateway.security.ldap.extended;

import org.georchestra.security.model.GeorchestraUser;
import org.georchestra.security.model.Organization;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.experimental.Delegate;

/**
 * {@link GeorchestraUser} with resolved {@link #getOrg() Organization}
 */
@SuppressWarnings("serial")
@RequiredArgsConstructor
@Accessors(chain = true)
public class ExtendedGeorchestraUser extends GeorchestraUser {

    @JsonIgnore
    private final @NonNull @Delegate GeorchestraUser user;

    @JsonIgnore
    private @Getter @Setter Organization org;

    public @Override boolean equals(Object o) {
        if (!(o instanceof GeorchestraUser)) {
            return false;
        }
        return super.equals(o);
    }

    public @Override int hashCode() {
        return super.hashCode();
    }

    @JsonIgnore
    private @Getter @Setter String orgUniqueId;
}
