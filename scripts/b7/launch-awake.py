import ctypes,subprocess,sys
ctypes.windll.kernel32.SetThreadExecutionState(0x80000001)
try:
 result=subprocess.run([sys.executable,'-X','utf8','scripts/b7/verify.py','--browser-channel','chrome'])
 sys.exit(result.returncode)
finally:ctypes.windll.kernel32.SetThreadExecutionState(0x80000000)
