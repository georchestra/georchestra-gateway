/*
 * Copyright (C) 2023 by the geOrchestra PSC
 *
 * This file is part of geOrchestra.
 *
 * geOrchestra is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * geOrchestra is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.georchestra.gateway.test.context.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

import org.springframework.core.annotation.AliasFor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.test.context.support.TestExecutionEvent;
import org.springframework.security.test.context.support.WithSecurityContext;
import org.springframework.test.context.TestContext;

/**
 * <ul>
 * <li>{@link #sub} string Subject - Identifier for the End-User at the Issuer.
 * <li>{@link #name} string End-User's full name in displayable form including
 * all name parts, possibly including titles and suffixes, ordered according to
 * the End-User's locale and preferences.
 * <li>{@link #given_name} string Given name(s) or first name(s) of the
 * End-User. Note that in some cultures, people can have multiple given names;
 * all can be present, with the names being separated by space characters.
 * <li>{@link #family_name} string Surname(s) or last name(s) of the End-User.
 * Note that in some cultures, people can have multiple family names or no
 * family name; all can be present, with the names being separated by space
 * characters.
 * <li>{@link #middle_name} string Middle name(s) of the End-User. Note that in
 * some cultures, people can have multiple middle names; all can be present,
 * with the names being separated by space characters. Also note that in some
 * cultures, middle names are not used.
 * <li>{@link #nickname} string Casual name of the End-User that may or may not
 * be the same as the given_name. For instance, a nickname value of Mike might
 * be returned alongside a given_name value of Michael.
 * <li>{@link #preferred_username} string Shorthand name by which the End-User
 * wishes to be referred to at the RP, such as janedoe or j.doe. This value MAY
 * be any valid JSON string including special characters such as @, /, or
 * whitespace. The RP MUST NOT rely upon this value being unique, as discussed
 * in Section 5.7.
 * <li>{@link #profile} string URL of the End-User's profile page. The contents
 * of this Web page SHOULD be about the End-User.
 * <li>{@link #picture} string URL of the End-User's profile picture. This URL
 * MUST refer to an image file (for example, a PNG, JPEG, or GIF image file),
 * rather than to a Web page containing an image. Note that this URL SHOULD
 * specifically reference a profile photo of the End-User suitable for
 * displaying when describing the End-User, rather than an arbitrary photo taken
 * by the End-User.
 * <li>{@link #website} string URL of the End-User's Web page or blog. This Web
 * page SHOULD contain information published by the End-User or an organization
 * that the End-User is affiliated with.
 * <li>{@link #email} string End-User's preferred e-mail address. Its value MUST
 * conform to the RFC 5322 [RFC5322] addr-spec syntax. The RP MUST NOT rely upon
 * this value being unique, as discussed in Section 5.7.
 * <li>{@link #email_verified} boolean True if the End-User's e-mail address has
 * been verified; otherwise false. When this Claim Value is true, this means
 * that the OP took affirmative steps to ensure that this e-mail address was
 * controlled by the End-User at the time the verification was performed. The
 * means by which an e-mail address is verified is context-specific, and
 * dependent upon the trust framework or contractual agreements within which the
 * parties are operating.
 * <li>{@link #gender} string End-User's gender. Values defined by this
 * specification are female and male. Other values MAY be used when neither of
 * the defined values are applicable.
 * <li>{@link #birthdate} string End-User's birthday, represented as an ISO
 * 8601:2004 [ISO8601‑2004] YYYY-MM-DD format. The year MAY be 0000, indicating
 * that it is omitted. To represent only the year, YYYY format is allowed. Note
 * that depending on the underlying platform's date related function, providing
 * just year can result in varying month and day, so the implementers need to
 * take this factor into account to correctly process the dates.
 * <li>{@link #zoneinfo} string String from zoneinfo [zoneinfo] time zone
 * database representing the End-User's time zone. For example, Europe/Paris or
 * America/Los_Angeles.
 * <li>{@link #locale} string End-User's locale, represented as a BCP47
 * [RFC5646] language tag. This is typically an ISO 639-1 Alpha-2 [ISO639‑1]
 * language code in lowercase and an ISO 3166-1 Alpha-2 [ISO3166‑1] country code
 * in uppercase, separated by a dash. For example, en-US or fr-CA. As a
 * compatibility note, some implementations have used an underscore as the
 * separator rather than a dash, for example, en_US; Relying Parties MAY choose
 * to accept this locale syntax as well.
 * <li>{@link #phone_number} string End-User's preferred telephone number. E.164
 * [E.164] is RECOMMENDED as the format of this Claim, for example, +1 (425)
 * 555-1212 or +56 (2) 687 2400. If the phone number contains an extension, it
 * is RECOMMENDED that the extension be represented using the RFC 3966 [RFC3966]
 * extension syntax, for example, +1 (604) 555-1234;ext=5678.
 * <li>{@link #phone_number_verified} boolean True if the End-User's phone
 * number has been verified; otherwise false. When this Claim Value is true,
 * this means that the OP took affirmative steps to ensure that this phone
 * number was controlled by the End-User at the time the verification was
 * performed. The means by which a phone number is verified is context-specific,
 * and dependent upon the trust framework or contractual agreements within which
 * the parties are operating. When true, the phone_number Claim MUST be in E.164
 * format and any extensions MUST be represented in RFC 3966 format.
 * <li>{@link #address} JSON object End-User's preferred postal address. The
 * value of the address member is a JSON [RFC4627] structure containing some or
 * all of the members defined in Section 5.1.1.
 * <li>{@link #updated_at} number Time the End-User's information was last
 * updated. Its value is a JSON number representing the number of seconds from
 * 1970-01-01T0:0:0Z as measured in UTC until the date/time.
 * </ul>
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@WithSecurityContext(factory = WithMockOidcSecurityContextFactory.class)
public @interface WithMockOidcUser {

    /**
     * {@link OAuth2AuthenticationToken#getAuthorizedClientRegistrationId()}
     */
    String clientRegistrationId() default "testclient";

    /**
     * The authorities to use. A {@link GrantedAuthority} will be created for each
     * value.
     */
    String[] authorities() default {};

    /**
     * List of key/value pairs of non-standard claims
     * 
     * @return
     */
    String[] nonStandardClaims() default {};

    /**
     * {@code sub}: Subject - Identifier for the End-User at the Issuer.
     */
    String sub() default "test-user";

    /**
     * {@code preferred_username}: Shorthand name by which the End-User wishes to be
     * referred to at the RP, such as janedoe or j.doe. This value MAY be any valid
     * JSON string including special characters such as @, /, or whitespace. The RP
     * MUST NOT rely upon this value being unique, as discussed in Section 5.7.
     */
    String preferred_username() default "user";

    /**
     * {@code name}: End-User's full name in displayable form including all name
     * parts, possibly including titles and suffixes, ordered according to the
     * End-User's locale and preferences.
     */
    String name() default "";

    /**
     * {@code given_name}: Given name(s) or first name(s) of the End-User.
     * <p>
     * Note that in some cultures, people can have multiple given names; all can be
     * present, with the names being separated by space characters.
     */
    String given_name() default "";

    /**
     * {@code family_name}: string Surname(s) or last name(s) of the End-User. Note
     * that in some cultures, people can have multiple family names or no family
     * name; all can be present, with the names being separated by space characters.
     */
    String family_name() default "";

    /**
     * {@code middle_name}: Middle name(s) of the End-User. Note that in some
     * cultures, people can have multiple middle names; all can be present, with the
     * names being separated by space characters. Also note that in some cultures,
     * middle names are not used.
     */
    String middle_name() default "";

    /**
     * {@code nickname}: string, Casual name of the End-User that may or may not be
     * the same as the given_name. For instance, a nickname value of Mike might be
     * returned alongside a given_name value of Michael.
     */
    String nickname() default "";

    /**
     * {@code profile}: string, URL of the End-User's profile page. The contents of
     * this Web page SHOULD be about the End-User.
     */
    String profile() default "";

    /**
     * {@code picture}: string, URL of the End-User's profile picture. This URL MUST
     * refer to an image file (for example, a PNG, JPEG, or GIF image file), rather
     * than to a Web page containing an image. Note that this URL SHOULD
     * specifically reference a profile photo of the End-User suitable for
     * displaying when describing the End-User, rather than an arbitrary photo taken
     * by the End-User.
     */
    String picture() default "";

    /**
     * {@code website}: string, URL of the End-User's Web page or blog. This Web
     * page SHOULD contain information published by the End-User or an organization
     * that the End-User is affiliated with.
     */
    String website() default "";

    /**
     * {@code email}: string, End-User's preferred e-mail address. Its value MUST
     * conform to the RFC 5322 [RFC5322] addr-spec syntax. The RP MUST NOT rely upon
     * this value being unique, as discussed in Section 5.7.
     */
    String email() default "";

    /**
     * {@code email_verified }: boolean, True if the End-User's e-mail address has
     * been verified; otherwise false. When this Claim Value is true, this means
     * that the OP took affirmative steps to ensure that this e-mail address was
     * controlled by the End-User at the time the verification was performed. The
     * means by which an e-mail address is verified is context-specific, and
     * dependent upon the trust framework or contractual agreements within which the
     * parties are operating.
     */
    boolean email_verified() default false;

    /**
     * {@code gender} string End-User's gender. Values defined by this specification
     * are female and male. Other values MAY be used when neither of the defined
     * values are applicable.
     */
    String gender() default "";

    /**
     * {@code birthdate}: string End-User's birthday, represented as an ISO
     * 8601:2004 [ISO8601‑2004] YYYY-MM-DD format. The year MAY be 0000, indicating
     * that it is omitted. To represent only the year, YYYY format is allowed. Note
     * that depending on the underlying platform's date related function, providing
     * just year can result in varying month and day, so the implementers need to
     * take this factor into account to correctly process the dates.
     * <li>zoneinfo string String from zoneinfo [zoneinfo] time zone database
     * representing the End-User's time zone. For example, Europe/Paris or
     * America/Los_Angeles.
     */
    String birthdate() default "";

    /**
     * {@code locale}: string End-User's locale, represented as a BCP47 [RFC5646]
     * language tag. This is typically an ISO 639-1 Alpha-2 [ISO639‑1] language code
     * in lowercase and an ISO 3166-1 Alpha-2 [ISO3166‑1] country code in uppercase,
     * separated by a dash. For example, en-US or fr-CA. As a compatibility note,
     * some implementations have used an underscore as the separator rather than a
     * dash, for example, en_US; Relying Parties MAY choose to accept this locale
     * syntax as well.
     */
    String locale() default "";

    /**
     * {@code phone_number}: string End-User's preferred telephone number. E.164
     * [E.164] is RECOMMENDED as the format of this Claim, for example, +1 (425)
     * 555-1212 or +56 (2) 687 2400. If the phone number contains an extension, it
     * is RECOMMENDED that the extension be represented using the RFC 3966 [RFC3966]
     * extension syntax, for example, +1 (604) 555-1234;ext=5678.
     */
    String phone_number() default "";

    /**
     * {@code phone_number_verified} boolean True if the End-User's phone number has
     * been verified; otherwise false. When this Claim Value is true, this means
     * that the OP took affirmative steps to ensure that this phone number was
     * controlled by the End-User at the time the verification was performed. The
     * means by which a phone number is verified is context-specific, and dependent
     * upon the trust framework or contractual agreements within which the parties
     * are operating. When true, the phone_number Claim MUST be in E.164 format and
     * any extensions MUST be represented in RFC 3966 format.
     */
    boolean phone_number_verified() default false;

    /**
     * {@code address} JSON object End-User's preferred postal address. The value of
     * the address member is a JSON [RFC4627] structure containing some or all of
     * the members defined in Section 5.1.1.
     */
    String address() default "";

    /**
     * {@code updated_at} number Time the End-User's information was last updated.
     * Its value is a JSON number representing the number of seconds from
     * 1970-01-01T0:0:0Z as measured in UTC until the date/time.
     * </ul>
     */
    long updated_at() default 0L;

    /**
     * Determines when the {@link SecurityContext} is setup. The default is before
     * {@link TestExecutionEvent#TEST_METHOD} which occurs during
     * {@link org.springframework.test.context.TestExecutionListener#beforeTestMethod(TestContext)}
     * 
     * @return the {@link TestExecutionEvent} to initialize before
     * @since 5.1
     */
    @AliasFor(annotation = WithSecurityContext.class)
    TestExecutionEvent setupBefore() default TestExecutionEvent.TEST_METHOD;

}
