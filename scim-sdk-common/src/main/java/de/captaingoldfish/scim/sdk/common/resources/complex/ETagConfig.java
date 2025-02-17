package de.captaingoldfish.scim.sdk.common.resources.complex;

import java.util.Optional;

import de.captaingoldfish.scim.sdk.common.constants.AttributeNames;
import de.captaingoldfish.scim.sdk.common.resources.base.ScimObjectNode;
import lombok.Builder;
import lombok.NoArgsConstructor;


/**
 * author Pascal Knueppel <br>
 * created at: 18.10.2019 - 11:12 <br>
 * <br>
 * A complex type that specifies ETag configuration options. REQUIRED.
 */
@NoArgsConstructor
public class ETagConfig extends ScimObjectNode
{

  @Builder
  public ETagConfig(Boolean supported)
  {
    super(null);
    setSupported(supported);
  }

  /**
   * A Boolean value specifying whether or not the operation is supported. REQUIRED.
   */
  public boolean isSupported()
  {
    return getBooleanAttribute(AttributeNames.RFC7643.SUPPORTED).orElse(false);
  }

  /**
   * A Boolean value specifying whether or not the operation is supported. REQUIRED.
   */
  public void setSupported(Boolean supported)
  {
    setAttribute(AttributeNames.RFC7643.SUPPORTED, Optional.ofNullable(supported).orElse(false));
  }

  /**
   * override lombok builder with public constructor
   */
  public static class ETagConfigBuilder
  {

    public ETagConfigBuilder()
    {}
  }
}
