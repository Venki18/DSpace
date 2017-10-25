/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.security;

import static org.dspace.app.rest.security.WebSecurityConfiguration.ADMIN_GRANT;
import static org.dspace.app.rest.security.WebSecurityConfiguration.EPERSON_GRANT;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.dspace.authenticate.AuthenticationMethod;
import org.dspace.authenticate.service.AuthenticationService;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.eperson.EPerson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class EPersonRestAuthenticationProvider implements AuthenticationProvider{

    private static final Logger log = LoggerFactory.getLogger(EPersonRestAuthenticationProvider.class);

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private AuthorizeService authorizeService;

    @Autowired
    private HttpServletRequest request;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        Context context = null;

        try {
            context = new Context();
            String name = authentication.getName();
            String password = authentication.getCredentials().toString();

            int implicitStatus = authenticationService.authenticateImplicit(context, null, null, null, request);

            if (implicitStatus == AuthenticationMethod.SUCCESS) {
                log.info(LogManager.getHeader(context, "login", "type=implicit"));
                return createAuthenticationToken(password, context);
            } else {
                int authenticateResult = authenticationService.authenticate(context, name, password, null, request);
                if (AuthenticationMethod.SUCCESS == authenticateResult) {

                    log.info(LogManager
                            .getHeader(context, "login", "type=explicit"));

                    return createAuthenticationToken(password, context);
                } else {
                    log.info(LogManager.getHeader(context, "failed_login", "email="
                            + name + ", result="
                            + authenticateResult));
                    throw new BadCredentialsException("Login failed");
                }
            }
        } catch (BadCredentialsException e){
            throw e;
        } catch (Exception e) {
            log.error("Error while authenticating in the rest api", e);
        } finally {
            if (context != null && context.isValid()) {
                try {
                    context.complete();
                } catch (SQLException e) {
                    log.error(e.getMessage() + " occurred while trying to close", e);
                }
            }
        }

        return null;
    }

    private Authentication createAuthenticationToken(final String password, final Context context) {
        EPerson ePerson = context.getCurrentUser();
        if(ePerson != null && StringUtils.isNotBlank(ePerson.getEmail())) {
            return new DSpaceAuthentication(ePerson.getEmail(), password, getGrantedAuthorities(context, ePerson));

        } else {
            log.info(LogManager.getHeader(context, "failed_login", "No eperson with an non-blank e-mail address found"));
            throw new BadCredentialsException("Login failed");
        }
    }

    public List<GrantedAuthority> getGrantedAuthorities(Context context, EPerson eperson) {
        List<GrantedAuthority> authorities = new LinkedList<>();

        if(eperson != null) {
            boolean isAdmin = false;
            try {
                isAdmin = authorizeService.isAdmin(context, eperson);
            } catch (SQLException e) {
                log.error("SQL error while checking for admin rights", e);
                //TODO FREDERIC throw exception to fail fast
            }

            if (isAdmin) {
                authorities.add(new SimpleGrantedAuthority(ADMIN_GRANT));
            } else {
                authorities.add(new SimpleGrantedAuthority(EPERSON_GRANT));
            }
        }

        return authorities;
    }

    public boolean supports(Class<?> authentication) {
        return authentication.equals(DSpaceAuthentication.class);
    }
}
