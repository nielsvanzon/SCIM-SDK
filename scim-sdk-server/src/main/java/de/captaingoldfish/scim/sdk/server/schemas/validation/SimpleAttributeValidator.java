package de.captaingoldfish.scim.sdk.server.schemas.validation;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;

import de.captaingoldfish.scim.sdk.common.constants.enums.ReferenceTypes;
import de.captaingoldfish.scim.sdk.common.constants.enums.Type;
import de.captaingoldfish.scim.sdk.common.exceptions.InvalidDateTimeRepresentationException;
import de.captaingoldfish.scim.sdk.common.resources.base.ScimBooleanNode;
import de.captaingoldfish.scim.sdk.common.resources.base.ScimDoubleNode;
import de.captaingoldfish.scim.sdk.common.resources.base.ScimIntNode;
import de.captaingoldfish.scim.sdk.common.resources.base.ScimLongNode;
import de.captaingoldfish.scim.sdk.common.resources.base.ScimTextNode;
import de.captaingoldfish.scim.sdk.common.schemas.SchemaAttribute;
import de.captaingoldfish.scim.sdk.common.utils.TimeUtils;
import de.captaingoldfish.scim.sdk.server.schemas.exceptions.AttributeValidationException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;


/**
 * @author Pascal Knüppel
 * @since 09.04.2021
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
class SimpleAttributeValidator
{

  /**
   * checks if the given node is a json leaf node
   *
   * @param attribute the attribute from the document
   * @return true if the attribute is a json leaf node, false else
   */
  protected static boolean isSimpleNode(JsonNode attribute)
  {
    return attribute.isNull() || (!attribute.isArray() && !attribute.isObject());
  }

  /**
   * will parse the given node type into a json representation that contains additional its schema attribute
   * definition
   */
  @SneakyThrows
  public static JsonNode parseNodeType(SchemaAttribute schemaAttribute, JsonNode attribute)
  {
    log.trace("Validating simple attribute '{}'", schemaAttribute.getScimNodeName());
    if (!isSimpleNode(attribute))
    {
      String errorMessage = String.format("Attribute '%s' is expected to be a simple attribute but is '%s'",
                                          schemaAttribute.getFullResourceName(),
                                          attribute);
      throw new AttributeValidationException(schemaAttribute, errorMessage);
    }
    checkCanonicalValues(schemaAttribute, attribute);

    Type type = schemaAttribute.getType();
    switch (type)
    {
      case STRING:
        isNodeOfExpectedType(schemaAttribute, attribute, jsonNode -> jsonNode.isTextual() || jsonNode.isObject());
        return new ScimTextNode(schemaAttribute, attribute.isTextual() ? attribute.textValue() : attribute.toString());
      case BINARY:
        isNodeOfExpectedType(schemaAttribute, attribute, jsonNode -> {
          if (jsonNode.isTextual())
          {
            try
            {
              Base64.getDecoder().decode(jsonNode.textValue());
              return true;
            }
            catch (IllegalArgumentException ex)
            {
              log.trace(ex.getMessage(), ex);
              log.debug(String.format("Data of attribute '%s' is not valid Base64 encoded data",
                                      schemaAttribute.getFullResourceName()));
              return false;
            }
          }
          return false;
        });
        return new ScimTextNode(schemaAttribute, attribute.textValue());
      case BOOLEAN:
        isNodeOfExpectedType(schemaAttribute, attribute, JsonNode::isBoolean);
        return new ScimBooleanNode(schemaAttribute, attribute.booleanValue());
      case INTEGER:
      {
        isNodeOfExpectedType(schemaAttribute,
                             attribute,
                             jsonNode -> jsonNode.isInt() || jsonNode.isLong() || jsonNode.isBigDecimal());
        if (attribute.intValue() == attribute.longValue())
        {
          return new ScimIntNode(schemaAttribute, attribute.intValue());
        }
        else
        {
          return new ScimLongNode(schemaAttribute, attribute.longValue());
        }
      }
      case DECIMAL:
        isNodeOfExpectedType(schemaAttribute,
                             attribute,
                             jsonNode -> jsonNode.isInt() || jsonNode.isLong() || jsonNode.isFloat()
                                         || jsonNode.isDouble() || jsonNode.isBigDecimal());
        return new ScimDoubleNode(schemaAttribute, attribute.doubleValue());
      case DATE_TIME:
        isNodeOfExpectedType(schemaAttribute, attribute, JsonNode::isTextual);
        parseDateTime(schemaAttribute, attribute.textValue());
        return new ScimTextNode(schemaAttribute, attribute.textValue());
      default:
        isNodeOfExpectedType(schemaAttribute, attribute, JsonNode::isTextual);
        validateValueNodeWithReferenceTypes(schemaAttribute, attribute);
        return new ScimTextNode(schemaAttribute, attribute.textValue());
    }
  }

  /**
   * checks if the given node is of the expected type
   *
   * @param schemaAttribute the meta attribute definition
   * @param valueNode the current value node that should be checked
   * @param isOfType the check that will validate if the node has the expected type
   */
  private static void isNodeOfExpectedType(SchemaAttribute schemaAttribute,
                                           JsonNode valueNode,
                                           Function<JsonNode, Boolean> ofType)
  {
    boolean isOfType = ofType.apply(valueNode);

    if (!isOfType)
    {
      Type type = schemaAttribute.getType();
      final String errorMessage = String.format("Value of attribute '%s' is not of type '%s' but of type '%s' with value '%s'",
                                                schemaAttribute.getFullResourceName(),
                                                type.getValue(),
                                                StringUtils.lowerCase(valueNode.getNodeType().toString()),
                                                valueNode);
      throw new AttributeValidationException(schemaAttribute, errorMessage);
    }
  }

  /**
   * tries to parse the given text as a xsd:datetime representation as defined in RFC7643 chapter 2.3.5
   */
  private static void parseDateTime(SchemaAttribute schemaAttribute, String textValue)
  {
    try
    {
      TimeUtils.parseDateTime(textValue);
    }
    catch (InvalidDateTimeRepresentationException ex)
    {
      throw new AttributeValidationException(schemaAttribute,
                                             String.format("Given value is not a valid dateTime '%s'", textValue));
    }
  }

  /**
   * validates a simple value node against the valid resource types defined in the meta schema
   *
   * @param schemaAttribute the meta attribute definition
   * @param valueNode the value node
   */
  private static void validateValueNodeWithReferenceTypes(SchemaAttribute schemaAttribute, JsonNode valueNode)
  {
    boolean isValidReferenceType = false;
    for ( ReferenceTypes referenceType : schemaAttribute.getReferenceTypes() )
    {
      switch (referenceType)
      {
        case RESOURCE:
        case URI:
          isValidReferenceType = parseUri(valueNode.textValue());
          break;
        case URL:
          isValidReferenceType = parseUrl(valueNode.textValue());
          break;
        default:
          isValidReferenceType = true;
      }
      if (isValidReferenceType)
      {
        break;
      }
    }
    if (!isValidReferenceType)
    {
      String errorMessage = String.format("Attribute '%s' is a referenceType and must apply to one of the following "
                                          + "types '%s' but value is '%s'",
                                          schemaAttribute.getFullResourceName(),
                                          schemaAttribute.getReferenceTypes(),
                                          valueNode.textValue());
      throw new AttributeValidationException(schemaAttribute, errorMessage);
    }
  }

  /**
   * tries to parse the given text into a URL
   */
  private static boolean parseUrl(String textValue)
  {
    try
    {
      new URL(textValue);
      return true;
    }
    catch (MalformedURLException ex)
    {
      log.debug(ex.getMessage());
      return false;
    }
  }

  /**
   * tries to parse the given text into a URI
   */
  private static boolean parseUri(String textValue)
  {
    try
    {
      new URI(textValue);
      return true;
    }
    catch (URISyntaxException ex)
    {
      log.debug(ex.getMessage());
      return false;
    }
  }

  /**
   * will verify that the current value node does define one of the canonical values of the attribute definition
   * if some are defined
   *
   * @param schemaAttribute the attribute definition from the meta schema
   * @param valueNode the value that matches to this definition
   */
  protected static void checkCanonicalValues(SchemaAttribute schemaAttribute, JsonNode valueNode)
  {
    if (schemaAttribute.getCanonicalValues().isEmpty())
    {
      // all values are valid
      return;
    }
    final String value = valueNode.textValue();
    AtomicBoolean caseInsensitiveMatch = new AtomicBoolean(false);
    Predicate<String> compare = s -> {
      if (schemaAttribute.isCaseExact())
      {
        caseInsensitiveMatch.compareAndSet(false, StringUtils.equalsIgnoreCase(s, value));
        return StringUtils.equals(s, value);
      }
      else
      {
        return StringUtils.equalsIgnoreCase(s, value);
      }
    };

    if (schemaAttribute.getCanonicalValues().stream().noneMatch(compare))
    {
      String errorMessage;
      if (schemaAttribute.isCaseExact() && caseInsensitiveMatch.get())
      {
        errorMessage = String.format("Attribute '%s' is caseExact and does not match its canonicalValues "
                                     + "'%s' actual value is '%s'",
                                     schemaAttribute.getFullResourceName(),
                                     schemaAttribute.getCanonicalValues(),
                                     value);
      }
      else
      {
        errorMessage = String.format("Attribute '%s' does not match one of its canonicalValues "
                                     + "'%s' actual value is '%s'",
                                     schemaAttribute.getFullResourceName(),
                                     schemaAttribute.getCanonicalValues(),
                                     value);
      }
      throw new AttributeValidationException(schemaAttribute, errorMessage);
    }
  }
}
