package network.vonix.viscord;

import net.fabricmc.api.ClientModInitializer;

/**
 * Client-side initializer for Viscord.
 * Viscord is primarily a server-side mod, so this is minimal.
 */
public class ViscordClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Viscord is primarily server-side, no client-specific logic needed
		Viscord.LOGGER.info("Viscord client initialized (server-side mod, minimal client code)");
	}
}