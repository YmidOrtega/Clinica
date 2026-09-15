package com.ClinicaDeYmid.auth_service.infrastructure.web;

import com.ClinicaDeYmid.auth_service.application.admin.UserAdministration;
import com.ClinicaDeYmid.auth_service.application.admin.UserDirectory;
import com.ClinicaDeYmid.auth_service.domain.user.Role;
import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.domain.user.UserException;
import com.ClinicaDeYmid.auth_service.domain.user.UserStatus;
import com.ClinicaDeYmid.auth_service.domain.user.Users;
import com.ClinicaDeYmid.auth_service.infrastructure.security.StaffPrincipal;
import com.ClinicaDeYmid.auth_service.infrastructure.web.UserViews.RevisionView;
import com.ClinicaDeYmid.auth_service.infrastructure.web.UserViews.UserSummaryView;
import com.ClinicaDeYmid.auth_service.infrastructure.web.UserViews.UserView;
import com.ClinicaDeYmid.commons.web.EntityTags;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(UserAdministrationController.BASE_PATH)
class UserAdministrationController {

    static final String BASE_PATH = "/api/v1/users";

    private static final int MAX_PAGE_SIZE = 50;

    private final UserAdministration administration;
    private final UserDirectory directory;

    UserAdministrationController(UserAdministration administration, UserDirectory directory) {
        this.administration = administration;
        this.directory = directory;
    }

    record Invitation(String email, String fullName, Role role) {
    }

    record Search(String text, Role role, UserStatus.Code status) {
    }

    record Rename(String fullName) {
    }

    record RoleChange(Role role) {
    }

    record Reason(String reason) {
    }

    @PostMapping
    ResponseEntity<UserView> invite(@AuthenticationPrincipal StaffPrincipal principal, @RequestBody Invitation body) {
        User invited = administration.invite(principal.caller(), body.email(), body.fullName(), body.role());
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + invited.uuid()))
                .eTag(EntityTags.of(invited.version()))
                .body(UserView.from(directory.details(invited)));
    }

    @PostMapping("/search")
    PagedModel<UserSummaryView> search(@RequestBody Search body, @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        if (page < 0) {
            throw new UserException.InvalidData("page", "no puede ser negativo");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new UserException.InvalidData("size", "debe estar entre 1 y " + MAX_PAGE_SIZE);
        }
        return new PagedModel<>(directory.search(new Users.Criteria(body.text(), body.role(), body.status()), PageRequest.of(page, size))
                .map(UserSummaryView::from));
    }

    @GetMapping("/{uuid}")
    ResponseEntity<UserView> get(@PathVariable UUID uuid) {
        return respond(directory.get(uuid));
    }

    @GetMapping("/{uuid}/history")
    List<RevisionView> history(@PathVariable UUID uuid) {
        return directory.history(uuid).stream().map(RevisionView::from).toList();
    }

    @PutMapping("/{uuid}/name")
    ResponseEntity<UserView> rename(@AuthenticationPrincipal StaffPrincipal principal, @PathVariable UUID uuid,
                                    @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch, @RequestBody Rename body) {
        return respond(administration.rename(principal.caller(), uuid, EntityTags.requiredVersion(ifMatch), body.fullName()));
    }

    @PutMapping("/{uuid}/role")
    ResponseEntity<UserView> changeRole(@AuthenticationPrincipal StaffPrincipal principal, @PathVariable UUID uuid,
                                        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch, @RequestBody RoleChange body) {
        return respond(administration.changeRole(principal.caller(), uuid, EntityTags.requiredVersion(ifMatch), body.role()));
    }

    @PostMapping("/{uuid}/suspension")
    ResponseEntity<UserView> suspend(@AuthenticationPrincipal StaffPrincipal principal, @PathVariable UUID uuid,
                                     @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch, @RequestBody Reason body) {
        return respond(administration.suspend(principal.caller(), uuid, EntityTags.requiredVersion(ifMatch), body.reason()));
    }

    @PostMapping("/{uuid}/deactivation")
    ResponseEntity<UserView> deactivate(@AuthenticationPrincipal StaffPrincipal principal, @PathVariable UUID uuid,
                                        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch, @RequestBody Reason body) {
        return respond(administration.deactivate(principal.caller(), uuid, EntityTags.requiredVersion(ifMatch), body.reason()));
    }

    @PostMapping("/{uuid}/reactivation")
    ResponseEntity<UserView> reactivate(@AuthenticationPrincipal StaffPrincipal principal, @PathVariable UUID uuid,
                                        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(administration.reactivate(principal.caller(), uuid, EntityTags.requiredVersion(ifMatch)));
    }

    @PostMapping("/{uuid}/password-change-requirement")
    ResponseEntity<UserView> requirePasswordChange(@AuthenticationPrincipal StaffPrincipal principal, @PathVariable UUID uuid,
                                                   @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                   @RequestBody Reason body) {
        return respond(administration.requirePasswordChange(principal.caller(), uuid, EntityTags.requiredVersion(ifMatch), body.reason()));
    }

    @PostMapping("/{uuid}/second-factor-reset")
    ResponseEntity<UserView> resetSecondFactor(@AuthenticationPrincipal StaffPrincipal principal, @PathVariable UUID uuid,
                                               @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                               @RequestBody Reason body) {
        return respond(administration.resetSecondFactor(principal.caller(), uuid, EntityTags.requiredVersion(ifMatch), body.reason()));
    }

    @PostMapping("/{uuid}/invitation")
    ResponseEntity<Void> resendInvitation(@AuthenticationPrincipal StaffPrincipal principal, @PathVariable UUID uuid) {
        administration.resendInvitation(principal.caller(), uuid);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{uuid}/unlock")
    ResponseEntity<UserView> unlock(@AuthenticationPrincipal StaffPrincipal principal, @PathVariable UUID uuid) {
        administration.unlock(principal.caller(), uuid);
        return respond(directory.get(uuid));
    }

    private ResponseEntity<UserView> respond(User user) {
        return respond(directory.details(user));
    }

    private static ResponseEntity<UserView> respond(UserDirectory.UserDetails details) {
        return ResponseEntity.ok().eTag(EntityTags.of(details.user().version())).body(UserView.from(details));
    }
}
