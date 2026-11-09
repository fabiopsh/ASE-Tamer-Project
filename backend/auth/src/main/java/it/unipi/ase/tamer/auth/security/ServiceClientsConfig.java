package it.unipi.ase.tamer.auth.security;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithParentName;
import java.util.Map;

/** {@code tamer.service-clients.<client_id>=<secret>}, written by init-secrets into secrets.properties. */
@ConfigMapping(prefix = "tamer.service-clients")
public interface ServiceClientsConfig {

    @WithParentName
    Map<String, String> secrets();
}
