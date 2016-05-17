#!/usr/bin/python
import webiopi
import os, sys

@webiopi.macro
def reboot():
    os.system("sudo shutdown -r now") # Send reboot command to os
    time.sleep(1) # Allow a sleep time of 1 second to reduce CPU usage    

if __name__ == '__main__':
	reboot()
