
package com.trouch.webiopi.client.devices.digital;

import com.trouch.webiopi.client.PiClient;

public class Macros extends GPIO {

	public Macros(PiClient client) {
		super(client, "");
		this.path = "/macros";
	}

}
