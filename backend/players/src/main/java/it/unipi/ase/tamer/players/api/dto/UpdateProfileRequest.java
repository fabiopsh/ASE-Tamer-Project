package it.unipi.ase.tamer.players.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** US-04: partial profile update; distinguishes omitted fields from explicit nulls. */
public class UpdateProfileRequest {

    private String displayName;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean displayNamePresent;

    private String avatar;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean avatarPresent;

    public UpdateProfileRequest() {
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        this.displayNamePresent = true;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
        this.avatarPresent = true;
    }

    @JsonIgnore
    public boolean hasDisplayName() {
        return displayNamePresent;
    }

    @JsonIgnore
    public boolean hasAvatar() {
        return avatarPresent;
    }
}
