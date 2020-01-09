package de.captaingoldfish.scim.sdk.client.springboot;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;


/**
 * author Pascal Knueppel <br>
 * created at: 11.12.2019 - 12:53 <br>
 * <br>
 * spring security configuration for this test that will enable mutual client authentication to test the http
 * tls client authentication
 */
@Profile(SecurityConstants.X509_PROFILE)
@Order(X509SecurityConfig.RANDOM_ORDER_NUMBER)
@Configuration
@EnableWebSecurity
public class X509SecurityConfig extends WebSecurityConfigurerAdapter
{

  /**
   * a order number that is given to this configuration that should not have any conflicts with other
   * spring-security configurations
   */
  public static final int RANDOM_ORDER_NUMBER = 499;

  /**
   * configure the endpoints that require mutual client authentication and add the regular expression to match
   * the username within the certificates distinguished name
   */
  @Override
  protected void configure(HttpSecurity http) throws Exception
  {
    http.csrf()
        .disable()
        .authorizeRequests()
        .antMatchers(AbstractSpringBootWebTest.TestController.GET_ENDPOINT_PATH,
                     AbstractSpringBootWebTest.TestController.TIMEOUT_ENDPOINT_PATH,
                     AbstractSpringBootWebTest.TestController.SCIM_ENDPOINT_PATH)
        .authenticated()
        .and()
        .x509()
        .subjectPrincipalRegex("CN=(.*)"); // the regular expression to parse the username from the DN
  }

  /**
   * will do the authentication if a request comes in. The CN of the certificate must match "test" to
   * successfully authenticate
   */
  @Bean
  public UserDetailsService userDetailsService()
  {
    return username -> {
      if (SecurityConstants.AUTHORIZED_USERNAME.equals(username))
      {
        return new User(username, "",
                        AuthorityUtils.commaSeparatedStringToAuthorityList(SecurityConstants.SUPER_ADMIN_ROLE));
      }
      else if (SecurityConstants.UNAUTHORIZED_USERNAME.equals(username))
      {
        return new User(username, "", AuthorityUtils.NO_AUTHORITIES);
      }
      else
      {
        return null;
      }
    };
  }
}
