package de.captaingoldfish.scim.sdk.server.endpoints.handler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.Assertions;

import de.captaingoldfish.scim.sdk.common.constants.AttributeNames;
import de.captaingoldfish.scim.sdk.common.constants.ResourceTypeNames;
import de.captaingoldfish.scim.sdk.common.constants.enums.SortOrder;
import de.captaingoldfish.scim.sdk.common.exceptions.ConflictException;
import de.captaingoldfish.scim.sdk.common.exceptions.ResourceNotFoundException;
import de.captaingoldfish.scim.sdk.common.resources.User;
import de.captaingoldfish.scim.sdk.common.resources.complex.Meta;
import de.captaingoldfish.scim.sdk.common.schemas.SchemaAttribute;
import de.captaingoldfish.scim.sdk.common.utils.JsonHelper;
import de.captaingoldfish.scim.sdk.server.endpoints.Context;
import de.captaingoldfish.scim.sdk.server.endpoints.ResourceHandler;
import de.captaingoldfish.scim.sdk.server.endpoints.validation.RequestValidator;
import de.captaingoldfish.scim.sdk.server.filter.FilterNode;
import de.captaingoldfish.scim.sdk.server.response.PartialListResponse;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;


/**
 * author Pascal Knueppel <br>
 * created at: 18.10.2019 - 00:05 <br>
 * <br>
 * a simple user resource handler for testing
 */
@Slf4j
public class UserHandlerImpl extends ResourceHandler<User>
{

  private final boolean returnETags;

  private final RequestValidator<User> requestValidator;

  private Consumer<Context> contextVerifier;

  @Getter
  private Map<String, User> inMemoryMap = new HashMap<>();

  public UserHandlerImpl(boolean returnETags)
  {
    this(returnETags, null);
  }

  public UserHandlerImpl(boolean returnETags, RequestValidator<User> requestValidator)
  {
    this.returnETags = returnETags;
    this.requestValidator = requestValidator;
    this.contextVerifier = context -> {};
  }

  public UserHandlerImpl(Consumer<Context> contextVerifier)
  {
    this(false, null);
    this.contextVerifier = Optional.ofNullable(contextVerifier).orElse(context -> {});
  }

  @Override
  public User createResource(User resource, Context context)
  {
    Optional.ofNullable(context).ifPresent(contextVerifier);
    Assertions.assertTrue(resource.getMeta().isPresent());
    Meta meta = resource.getMeta().get();
    Assertions.assertTrue(meta.getResourceType().isPresent());
    Assertions.assertEquals(ResourceTypeNames.USER, meta.getResourceType().get());
    final String userId = UUID.randomUUID().toString();
    if (inMemoryMap.containsKey(userId))
    {
      throw new ConflictException("resource with id '" + userId + "' does already exist");
    }
    resource.setId(userId);
    inMemoryMap.put(userId, resource);
    resource.remove(AttributeNames.RFC7643.META);
    Instant created = Instant.now();
    resource.setMeta(Meta.builder().created(created).build());
    return resource;
  }

  @Override
  public User getResource(String id,
                          List<SchemaAttribute> attributes,
                          List<SchemaAttribute> excludedAttributes,
                          Context context)
  {
    Optional.ofNullable(context).ifPresent(contextVerifier);
    User user = inMemoryMap.get(id);
    if (user != null)
    {
      Meta meta = user.getMeta().orElse(Meta.builder().build());
      user.remove(AttributeNames.RFC7643.META);
      user.setMeta(Meta.builder()
                       .created(meta.getCreated().orElse(null))
                       .lastModified(meta.getLastModified().orElse(null))
                       .version(returnETags ? meta.getVersion().orElse(null) : null)
                       .build());
    }
    return Optional.ofNullable(user).map(u -> JsonHelper.copyResourceToObject(u.deepCopy(), User.class)).orElse(null);
  }

  @Override
  public PartialListResponse<User> listResources(long startIndex,
                                                 int count,
                                                 FilterNode filter,
                                                 SchemaAttribute sortBy,
                                                 SortOrder sortOrder,
                                                 List<SchemaAttribute> attributes,
                                                 List<SchemaAttribute> excludedAttributes,
                                                 Context context)
  {
    Optional.ofNullable(context).ifPresent(contextVerifier);
    List<User> resourceNodes = new ArrayList<>(inMemoryMap.values());
    resourceNodes.forEach(user -> {
      Meta meta = user.getMeta().get();
      user.remove(AttributeNames.RFC7643.META);
      user.setMeta(Meta.builder()
                       .created(meta.getCreated().get())
                       .lastModified(meta.getLastModified().get())
                       .version(returnETags ? meta.getVersion().orElse(null) : null)
                       .build());
    });
    return PartialListResponse.<User> builder().resources(resourceNodes).totalResults(resourceNodes.size()).build();
  }

  @Override
  public User updateResource(User resource, Context context)
  {
    Optional.ofNullable(context).ifPresent(contextVerifier);
    Assertions.assertTrue(resource.getMeta().isPresent());
    Meta meta = resource.getMeta().get();
    Assertions.assertTrue(meta.getLocation().isPresent());
    Assertions.assertTrue(meta.getResourceType().isPresent());
    Assertions.assertEquals(ResourceTypeNames.USER, meta.getResourceType().get());
    String userId = resource.getId().get();
    User oldUser = inMemoryMap.get(userId);
    if (oldUser == null)
    {
      throw new ResourceNotFoundException("resource with id '" + userId + "' does not exist", null, null);
    }
    inMemoryMap.put(userId, resource);
    Meta oldMeta = oldUser.getMeta().get();

    oldUser.remove(AttributeNames.RFC7643.META);
    resource.remove(AttributeNames.RFC7643.META);

    Instant lastModified = null;
    if (!oldUser.equals(resource))
    {
      lastModified = Instant.now();
    }
    resource.setMeta(Meta.builder()
                         .created(oldMeta.getCreated().get())
                         .lastModified(lastModified)
                         .version(returnETags ? oldMeta.getVersion().orElse(null) : null)
                         .build());
    return resource;
  }

  @Override
  public void deleteResource(String id, Context context)
  {
    Optional.ofNullable(context).ifPresent(contextVerifier);
    if (inMemoryMap.containsKey(id))
    {
      inMemoryMap.remove(id);
    }
    else
    {
      throw new ResourceNotFoundException("resource with id '" + id + "' does not exist", null, null);
    }
  }

  @Override
  public RequestValidator<User> getRequestValidator()
  {
    return requestValidator;
  }
}
