package de.gold.scim.endpoints;

import java.lang.reflect.ParameterizedType;

import com.fasterxml.jackson.databind.JsonNode;

import de.gold.scim.exceptions.InternalServerException;
import de.gold.scim.utils.JsonHelper;
import lombok.Getter;


/**
 * author Pascal Knueppel <br>
 * created at: 07.10.2019 - 23:17 <br>
 * <br>
 */
public abstract class ResourceHandler<T extends JsonNode>
{

  @Getter
  private Class<T> type;

  public ResourceHandler()
  {
    ParameterizedType parameterizedType = (ParameterizedType)getClass().getGenericSuperclass();
    this.type = (Class<T>)parameterizedType.getActualTypeArguments()[0];
  }

  public abstract T createResource(T resource);

  public abstract T readResource(String id);

  public abstract T listResources();

  public abstract T updateResource(T resource, String id);

  public abstract T deleteResource(String id);

  protected T copyResourceToObject(T resource)
  {
    T type = getNewInstance();
    resource.fields().forEachRemaining(stringJsonNodeEntry -> {
      JsonHelper.addAttribute(type, stringJsonNodeEntry.getKey(), stringJsonNodeEntry.getValue());
    });
    return type;
  }

  private T getNewInstance()
  {
    try
    {
      return getType().newInstance();
    }
    catch (InstantiationException | IllegalAccessException e)
    {
      throw new InternalServerException("could not create instance of type '" + getType() + "': " + e.getMessage(), e,
                                        null);
    }
  }

}