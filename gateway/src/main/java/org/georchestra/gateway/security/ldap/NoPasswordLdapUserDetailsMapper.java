package org.georchestra.gateway.security.ldap;

import org.springframework.security.ldap.userdetails.LdapUserDetailsMapper;

public class NoPasswordLdapUserDetailsMapper extends LdapUserDetailsMapper {

    @Override
    protected String mapPassword(Object passwordValue) {
        return null;
    }
}
