package de.captaingoldfish.scim.sdk.server.endpoints.validation;

import de.captaingoldfish.scim.sdk.common.exceptions.ScimException;
import lombok.Getter;


/**
 * @author Pascal Knueppel
 * @since 25.04.2021
 */
public class RequestContextException extends ScimException
{

  @Getter
  private final ValidationContext validationContext;

  public RequestContextException(ValidationContext validationContext)
  {
    super("The request document contains errors", null, validationContext.getHttpResponseStatus(), null,
          validationContext.getResponseHttpHeaders());
    this.validationContext = validationContext;
  }
}
