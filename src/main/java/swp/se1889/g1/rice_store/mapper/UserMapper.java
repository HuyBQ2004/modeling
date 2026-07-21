package swp.se1889.g1.rice_store.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import swp.se1889.g1.rice_store.dto.request.UpdateUserRequest;
import swp.se1889.g1.rice_store.dto.request.UserRequest;
import swp.se1889.g1.rice_store.entity.User;

@Mapper(componentModel = "spring")
public interface UserMapper {
    User toUser(UserRequest request);

    void updateUserFromRequest(UpdateUserRequest request, @MappingTarget User user);
}
