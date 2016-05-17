import webiopi
import datetime

GPIO = webiopi.GPIO

#PINS
LIGHT = 13
TOGGLE = 18

#TIME
HOURON  = 9 #+1
HOUROFF = 22 #+1
MINON = 29 #-1

# called at WebIOPi startup
def setup():
    GPIO.setFunction(LIGHT, GPIO.OUT)
    GPIO.setFunction(TOGGLE, GPIO.OUT)
    now = datetime.datetime.now()

@webiopi.macro
def timehr():
    now = datetime.datetime.now()
    return "%d" % (now.hour)

@webiopi.macro
def timemin():
    now = datetime.datetime.now()
    return "%d" % (now.minute)
    
def turnOff():
    # turn off only if on
    if(GPIO.digitalRead(LIGHT) == GPIO.LOW):
        GPIO.digitalWrite(LIGHT, GPIO.HIGH)
        
def turnOn():
    # turn on only if off
    if(GPIO.digitalRead(LIGHT) == GPIO.HIGH):
        GPIO.digitalWrite(LIGHT, GPIO.LOW)
    
def loop():
    # update time
    now = datetime.datetime.now()

    # see what to do
    if(((HOURON < now.hour < HOUROFF) and (MINON < now.minute)) and (GPIO.digitalRead(TOGGLE) == GPIO.LOW)):
        turnOn()
    else:
        turnOff()

    # rest
    webiopi.sleep(10)

def destroy():
    GPIO.digitalWrite(LIGHT, GPIO.LOW)
