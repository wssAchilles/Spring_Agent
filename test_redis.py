import redis
import json
import time

r = redis.Redis(host='localhost', port=6379, db=0)
session_id = "test-session-123"

# Hermes memory structures usually store messages in a list or similar.
# The sleep agent checks last activity time. Let's see if we can find how it's stored.
keys = r.keys('*')
print("Existing redis keys:", keys)
