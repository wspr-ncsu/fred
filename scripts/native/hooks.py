import angr
from signature import SignatureParser
import traceback

DEBUG = False

class RegisterNativeMethods(angr.SimProcedure):
	def __init__(self, fred):
		super().__init__()
		#self.func_ptrs = {}
		self.fred = fred

	def run(self, env, class_name, g_methods, num_methods):
		#print("SimProc: jniRegisterNativeMethods ", env, class_name, g_methods, num_methods)


			# if g_methods.ast.uninitialized == True:
		# 	print("Unknown gMethods structure - Cannot understand argument list")
		# 	return

		try:
			method_num = num_methods.ast.args[0]
			javaClass = str(self.state.mem[class_name].string.concrete, "utf-8")

			if type(method_num) == str:
				print(method_num)

		except Exception as e:
			traceback.print_exc()

		try:
			method = self.state.mem[g_methods].struct.JNINativeMethod.array(method_num)
			for i in range(method_num):
				try:
					name = str(method[i].name.deref.string.concrete, "utf-8")
					signature = str(method[i].signature.deref.string.concrete, "utf-8")
					fn_ptr = method[i].fnPtr.resolved.args[0]

					sigparse = SignatureParser(signature)

					function = (name, sigparse.param_list, sigparse.returns, javaClass)

					if fn_ptr == 0:
						# If we can't find func ptr, try to search in the symbols table.
						for im in self.fred.proj.loader.main_object.symbols:
							if "JNIEnv" in im.name:
								if name in im.name:
									fn_ptr = im.rebased_addr
									print(name + " (1) fn_ptr received from symbol table " + str(hex(fn_ptr)) + " from " + im.name)
									break
								elif name.startswith("native"):
									newname = name[6:]
									# Try again
									if newname in im.name:
										fn_ptr = im.rebased_addr
										print(name + " (2) fn_ptr received from symbol table " +
										      str(hex(fn_ptr)) + " from " + im.name)
										break
								elif name.endswith("native"):
									newname = name[:6]
									# Try again
									if newname in im.name:
										fn_ptr = im.rebased_addr
										print(name + " (3) fn_ptr received from symbol table " +
										      str(hex(fn_ptr)) + " from " + im.name)
										break

					if DEBUG == True:
						print("method name: " + name + " signature: " +
											signature + " ptr: " + hex(int(fn_ptr)))
						with open("jnimeths.txt", "a") as f:
							f.write("method name: " + name + " signature: " +
											signature + " ptr: " + hex(int(fn_ptr)) + "\r\n")

					if fn_ptr != 0:
						self.fred.func_ptrs[fn_ptr] = function

				except Exception as e:
					traceback.print_exc()
		except Exception as e:
			traceback.print_exc()




#class fopen(angr.SimProcedure):
#     def run(self, file, mode):
#         print(file)

#         print(self.state.mem[file])
#         print(self.state.mem[mode])

# rd_list = list()
# proj.hook_symbol("fopen", fopen(), replace=True)
