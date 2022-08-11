# Setup
from pathlib import Path
import angr
import monkeyhex
import archinfo
import json
import angr.analyses.reaching_definitions.dep_graph as dep_graph
import sys
import os
import traceback
from angr import *
from angr.engines.light import SpOffset
from config import file_functions
from hooks import RegisterNativeMethods
import signal

# In seconds
TIMEOUT_TIME = 18000

class TimeoutException(Exception):   # Custom exception class
    pass

def timeout_handler(signum, frame):   # Custom signal handler
    raise TimeoutException

signal.signal(signal.SIGALRM, timeout_handler)

DEBUG = False


def serialize_sets(obj):
    if isinstance(obj, set):
        return list(obj)
    else:
    	return list(obj)

class FReD:
	def __init__(self, file):
		angr.types.register_types(angr.types.parse_type('struct JNINativeMethod {const char* name;const char* signature;void* fnPtr;}'))
		self.proj = angr.Project(str(file), default_analysis_mode='symbolic', load_options={'auto_load_libs': False, 'main_opts': {'base_addr': 0x0}})
		if self.proj == None:
			print("Could not create project")
			self.proj = None
			return

		# Make sure JNI is in this library
		self.jniOnLoad = self.proj.loader.find_symbol('JNI_OnLoad')

		found = False
		self.file_functions_addr = dict()
		for func in file_functions:
			addr_list = self.proj.loader.find_all_symbols(func)
			if addr_list != None:
				for address in addr_list:
					self.file_functions_addr[address.rebased_addr] = func
					found = True
				#break
			# Also try to find objects in the main plt that refer to our file functions.
			if func in self.proj.loader.main_object.plt:
				self.file_functions_addr[self.proj.loader.main_object.plt[func]] = func

		if found != True or len(self.file_functions_addr) == 0:
			print("Did not find any file functions in the .so file.")
			self.proj = None
			return

		if DEBUG == True:
			with open("output.txt", "w") as f:
				for im, sym in self.proj.loader.main_object.imports.items():
					f.write("import " + str(im) + " at " + str(hex(sym.rebased_addr)) + "\r\n")
				for im in self.proj.loader.main_object.symbols:
					f.write("symbol " + str(im) + "\r\n")
				for ex in self.proj.loader.extern_object.symbols:
					f.write("extern " + str(ex) + "\r\n")

		# Try to generate a CFG with indirect jumps first. If it doesn't work, failover to one without resolving indirect jmp's.
		try:
			self.cfgFast = self.proj.analyses.CFGFast(fail_fast=True, data_references=True, cross_references=True, resolve_indirect_jumps=True, exclude_sparse_regions=False)
		except Exception as e:
			try:
				self.cfgFast = self.proj.analyses.CFGFast(fail_fast=True, exclude_sparse_regions=False, data_references=True, cross_references=True, resolve_indirect_jumps=False)
			except Exception as ee:
				try:
					self.cfgFast = self.proj.analyses.CFGFast(fail_fast=True, exclude_sparse_regions=True, data_references=False, cross_references=False, resolve_indirect_jumps=False)
				except Exception as eee:
					traceback.print_exc()

		#self.cfgFast = self.proj.analyses.CFGEmulated(
		#	fail_fast=True, resolve_indirect_jumps=False)

		self.state_options = angr.options.unicorn.union(angr.options.resilience).union(
		    angr.options.refs).union(angr.options.simplification).union(angr.options.approximation)

		self.state_options.add(angr.options.CONCRETIZE)
		self.state_options.add(angr.options.REVERSE_MEMORY_NAME_MAP)
		self.state_options.add(angr.options.UNICORN_AGGRESSIVE_CONCRETIZATION)

		if self.cfgFast is None or len(self.cfgFast.graph.nodes()) == 0 or len(self.cfgFast.graph.edges()) == 0:
			print("CFG empty")
			self.proj = None
			return

		self.registerNativeMethodsNames = [
			"__ThumbV7PILongThunk_jniRegisterNativeMethods",
			"jniRegisterNativeMethods",
			"_ZN7android14AndroidRuntime21registerNativeMethodsEP7_JNIEnvPKcPK15JNINativeMethodi",
			"__ThumbV7PILongThunk__ZN7android14AndroidRuntime21registerNativeMethodsEP7_JNIEnvPKcPK15JNINativeMethodi",
			"RegisterNatives"
			"_ZN9conscrypt7jniutil24jniRegisterNativeMethodsEP7_JNIEnvPKcPK15JNINativeMethodi"
		]

		self.jniRegisterNativesSymbols = []
		for symbol in self.registerNativeMethodsNames:
			found_sym = self.proj.loader.find_symbol(symbol)
			if found_sym is not None:
				self.jniRegisterNativesSymbols.append(found_sym.rebased_addr)

		# Finally, look for any symbols that end in "egisterNativeMethodsEP7_JNIEnvPKcPK15JNINativeMethodi" as thats probably what we need.
		for im in self.proj.loader.main_object.symbols:
			if im.name.endswith("egisterNativeMethodsEP7_JNIEnvPKcPK15JNINativeMethodi") and im.rebased_addr not in self.jniRegisterNativesSymbols:
				self.jniRegisterNativesSymbols.append(im.rebased_addr)

		self.findRegisterMethodsPred()


	def findRegisterMethodsPred(self):

		# get CFGNode for jniRegisterNativeMethods
		self.jniRegisterNatives = set()

		if self.jniOnLoad is not None:
			for sym in self.jniRegisterNativesSymbols:
				self.jniRegisterNatives.add((self.jniOnLoad.rebased_addr, sym))
			if len(self.jniRegisterNatives) == 0:
				self.jniRegisterNatives.add((self.jniOnLoad.rebased_addr, None))

		for symbol in self.jniRegisterNativesSymbols:

			registerNativeMethodsCFG = self.cfgFast.get_all_nodes(symbol)
			if registerNativeMethodsCFG is None:
				print("couldn't find " + str(symbol) + " in the CFG")
				continue

			for node in registerNativeMethodsCFG:
				preds = self.cfgFast.get_all_predecessors(node, depth_limit=3)

				for pred in preds:

					self.jniRegisterNatives.add((pred.function_address, symbol))


	def createCFGEmulated(self, ptr, cd=None):

		self.start_state = self.proj.factory.full_init_state(
		    mode="symbolic", addr=ptr, add_options=self.state_options)

		cfg = None

		# Terminate after sometime
		signal.alarm(TIMEOUT_TIME)

		try:
			cfg = self.proj.analyses.CFGEmulated(fail_fast=True, initial_state=self.start_state, starts=[
			                                     ptr], resolve_indirect_jumps=True, context_sensitivity_level=5, enable_function_hints=False, keep_state=True, normalize=True, call_depth=cd)
		except TimeoutException as e:
			return None
		except Exception as e:
			# Reset alarm
			signal.alarm(TIMEOUT_TIME)
			try:
				cfg = self.proj.analyses.CFGEmulated(fail_fast=True, initial_state=self.start_state, starts=[
                                    ptr], resolve_indirect_jumps=False, context_sensitivity_level=1, enable_function_hints=False, keep_state=True, normalize=True, call_depth=cd)
			except Exception as ee:
				if DEBUG == True:
					traceback.print_exc()

				return None
			finally:
				signal.alarm(0)
		finally:
			signal.alarm(0)

		return cfg

	def findJNIMethods(self):
		self.func_ptrs = {}
		self.RegisterNativeMethods = RegisterNativeMethods(fred=self)

		for addr in self.jniRegisterNativesSymbols:
			self.proj.hook(addr, self.RegisterNativeMethods, replace=True)

		# Make CFG from start state to jniRegisterMethods

		if len(self.jniRegisterNatives) == 0:
			print("No call to jniRegisterNativeMethods found")
			return 0

		for ptr, goal in self.jniRegisterNatives:

			cfg = self.createCFGEmulated(ptr, 3)
			#cfg = None
			if cfg is not None:
				self.cfg =  cfg
			elif goal != None:
				# Try a directed symbolic execution approach

				self.start_state = self.proj.factory.full_init_state(
					addr=ptr, add_options=self.state_options)

				self.simgr = self.proj.factory.simgr(self.start_state)

				director = angr.exploration_techniques.Director(cfg_keep_states=True)

				goal_func = self.cfgFast.kb.functions.function(addr=goal)
				director.add_goal(angr.exploration_techniques.CallFunctionGoal(goal_func, []))

				self.simgr.use_technique(director)

				# Terminate after a long time
				signal.alarm(TIMEOUT_TIME)

				try:
					self.simgr.explore(find=goal)
				except TimeoutException as e:
					print("Terminated state " + str(self.start_state))
					continue
				except Exception as e:
					continue
				finally:
					signal.alarm(0)

				self.simgr.step(stash='found')

		return 1

	def findFileFunctionsForJNICall(self, ptr, funcName):
		self.found_funcs = list()

		# Replace CFG with one that only considers a starting point at one of our identified JNI methods.
		try:
			self.cfgFaster = self.proj.analyses.CFGFast(fail_fast=True, data_references=True, cross_references=True, resolve_indirect_jumps=True,
			                                          exclude_sparse_regions=True, symbols=False, force_complete_scan=False, start_at_entry=False, normalize=True, function_prologues=False, force_segment=True, function_starts=[ptr])
		except Exception as e:
			self.cfgFaster = self.proj.analyses.CFGFast(fail_fast=True, data_references=True, cross_references=True,
			                                          resolve_indirect_jumps=False, exclude_sparse_regions=True, symbols=False, force_complete_scan=False, normalize=True, function_prologues=False, force_segment=True, start_at_entry=False, function_starts=[ptr])

		jniMethod = self.cfgFaster.kb.functions.function(addr=ptr)

		if jniMethod is None:
			print("Could not find jniMethod at " + str(hex(ptr)))
			return 0

		#jni_to_file_func = {}

		for addr, name in self.file_functions_addr.items():
			file_function_node = self.cfgFaster.kb.functions.function(addr=addr)

			if file_function_node is not None:
				target_node = self.cfgFaster.get_any_node(file_function_node.addr)

				if target_node is not None:
					self.found_funcs.append(
						(funcName, jniMethod, file_function_node, target_node, name))

	def dataFlowAnalysis(self):
		rd_list = []
		self.nodefs = {}
		for function, jnifunc, cfg_func, target, target_name in self.found_funcs:
			# Find all preceding functions to our target file function.
			# This gives us all predecessaors that call the file function
			# Add it to the observation points just in case

			preds = target.predecessors
			observation_points = []

			for pred in preds:
				xref_addr = self.proj.factory.block(pred.addr).instruction_addrs[-1]
				observation_points.append(("insn", xref_addr, 0))

			observation_points.append(("insn", target.addr, 0))

			# for arg in jnifunc.arguments:
			# 	print(arg)

			#observation_points.append(("insn", jnifunc.addr, 0))

			try:
				rd = self.proj.analyses.ReachingDefinitions(subject=jnifunc,
												func_graph=jnifunc.graph,
												cc=jnifunc.calling_convention,
												observation_points=observation_points,
												dep_graph=dep_graph.DepGraph()
												)
			except Exception as e:
				# Sorry for this, sometimes it explodes :)
				traceback.print_exc()
				continue

			if len(rd.observed_results) > 0:
				rd_list.append((rd, function, jnifunc, cfg_func, preds))
			else:
				if function[0] not in self.nodefs.keys():
					#self.nodefs[function[0]][target_name] = []
					self.addAuxInfo(self.nodefs, function, jnifunc, target_name, preds)

					# Only add this if we have no other defs
					self.nodefs[function[0]][target_name]["results"].add("Found no variable changes related to the params of function call.")


		return rd_list

	def addAuxInfo(self, arr, function, jnifunc, file_func, preds):
		funcName = function[0]

		if funcName not in arr:
			arr[funcName] = {}

			if "strings" not in arr[funcName]:
				arr[funcName]["strings"] = set()

				for addr, string in jnifunc.string_references():
					arr[funcName]["strings"].add(string)

			if "params" not in arr[funcName]:
				arr[funcName]["params"] = function[1]

				arr[funcName]["returns"] = function[2]

				arr[funcName]["class"] = function[3]

		if file_func not in arr[funcName]:
			arr[funcName][file_func] = {}

		if "results" not in arr[funcName][file_func]:
			arr[funcName][file_func]["results"] = set()

		if "predecessors" not in arr[funcName][file_func]:
			arr[funcName][file_func]["predecessors"] = set()

			for pred in preds:
				func = self.cfgFast.kb.functions.function(addr=pred.function_address)
				if func is not None and func.demangled_name is not None:
					arr[funcName][file_func]["predecessors"].add(func.demangled_name)
				elif pred.name is not None:
					arr[funcName][file_func]["predecessors"].add(pred.name)

	def mergeResults(self, parsedResults, f):
		if len(parsedResults) == 0 or not os.path.exists(f) or os.stat(f).st_size == 0:
			return parsedResults

		to_copy = ["strings"]
		to_copy_file_func = ["results", "predecessors"]

		try:
			with open(f, "r") as file:
				data = json.load(file)
				for i in range(len(data)):
					f = next(iter(data[i]))
					if f in parsedResults:
						for arg in to_copy:
							if arg in data[i][f].keys():
								parsedResults[f][arg] = parsedResults[f][arg].union(set(data[i][f][arg]))
						for arg in data[i][f].keys():
							if arg not in self.ignored_json_keys:
								# it's a function name
								for argument in to_copy_file_func:
									if argument in data[i][f][arg]:
										if argument not in parsedResults[f][arg]:
											parsedResults[f][arg][argument] = data[i][f][arg][argument]
										else:
											parsedResults[f][arg][argument] = parsedResults[f][arg][argument].union(
												set(data[i][f][arg][argument]))
		except FileNotFoundError:
			print("Previous file not found")
		except Exception:
			print("Exception occurred")
			traceback.print_exc()
		return parsedResults

	def padPreviousResults(self, curResults, f):

		if not os.path.exists(f) or os.stat(f).st_size == 0:
			return curResults

		try:
			with open(f, "r") as file:
				data = json.load(file)

				if len(curResults) == 0:
					return data
				else:

					for i in range(len(data)):
						f = next(iter(data[i]))
						found = False
						for result in curResults:
							c = next(iter(result))
							if c == f:
								found = True
								break
						if found == False:
							curResults.append(data[i])

		except Exception as e:
			traceback.print_exc()
		return curResults

	def parseResults(self, rd_list):

		ADDRESSES = []

		for rd, function, jniMethod, file_function_node, preds in rd_list:
			#print(rd.observed_results)

			if rd.observed_results != {}:
				for result in rd.observed_results.items():
					defs = [result[1].stack_definitions, result[1].register_definitions, result[1].heap_definitions, result[1].memory_definitions]
					for defType in defs:
						for definition in defType.get_all_variables():
							firstElement = definition.data.get_first_element()
							if type(firstElement) != angr.knowledge_plugins.key_definitions.undefined.Undefined:
								ADDRESSES.append((firstElement, definition.codeloc.ins_addr if definition.codeloc.ins_addr is not None else definition.codeloc.block_addr,
                                                            function, jniMethod, file_function_node, preds))

					# temp_defs a little different structure, has no .stored_object

					# for key, itemSet in result[1].tmp_definitions.items():
					# 	for item in itemSet:
					# 		if type(item.data.get_first_element()) != angr.knowledge_plugins.key_definitions.undefined.Undefined:

					# 			ADDRESSES.add((item.data.get_first_element(
					# 			), item.codeloc.ins_addr if item.codeloc.ins_addr is not None else item.codeloc.block_addr, function, jniMethod, file_function_node, jniCFG))

			# Get the VEX offset for "r0"
			# reg_vex_offset = proj.arch.registers.get("r0", None)[0]

		main_obj = self.proj.loader.main_object

		results = {}
		args = {}

		for addr, codeaddr, function, jnifunc, target_file_func, preds in ADDRESSES:

			file_func = str(target_file_func.name)

			funcName = function[0]

			self.addAuxInfo(results, function, jnifunc, file_func, preds)
			# if funcName not in results:
			# 	results[funcName] = {}

			# 	if "strings" not in results[funcName]:
			# 		results[funcName]["strings"] = []

			# 		for addr, string in jnifunc.string_references():
			# 			results[funcName]["strings"].append(string)

			# 	if "params" not in results[funcName]:
			# 		results[funcName]["params"] = function[1]

			# 		results[funcName]["returns"] = function[2]

			# if file_func not in results[funcName]:
			# 	results[funcName][file_func] = {}

			# if "results" not in results[funcName][file_func]:
			# 	results[funcName][file_func]["results"] = []

			# if "predecessors" not in results[funcName][file_func]:
			# 	results[funcName][file_func]["predecessors"] = []

			# 	for pred in preds:
			# 		func = self.cfgFast.kb.functions.function(addr=pred.function_address)
			# 		if func is not None and func.demangled_name is not None:
			# 			results[funcName][file_func]["predecessors"].append(func.demangled_name)
			# 		elif pred.name is not None:
			# 			results[funcName][file_func]["predecessors"].append(pred.name)

			if type(addr) == int:
				if addr > main_obj.min_addr and addr < main_obj.max_addr:
					section = main_obj.find_section_containing(addr)
					if section is not None and section.is_readable and not section.is_executable:
						#print(hex(addr))
						try:
							strTest = self.start_state.mem[addr].string.concrete
						except AttributeError:
							self.start_state = self.proj.factory.full_init_state(
							    mode="symbolic", addr=jnifunc.addr, add_options=self.state_options)
							strTest = self.start_state.mem[addr].string.concrete

						if len(strTest) == 0:
							# Try integer
							results[funcName][file_func]["results"].add(
							    self.start_state.solver.eval(self.start_state.mem[addr].int.resolved))

							continue;

						try:
							results[funcName][file_func]["results"].add(str(strTest, "utf-8"))
						except UnicodeDecodeError:
							# Just don't try to decode
							results[funcName][file_func]["results"].add(str(strTest))

			else:

				mem_addr = self.start_state.regs.sp + addr.offset
				readable_mem = self.start_state.mem[mem_addr]
				try:
					results[funcName][file_func]["results"].add(
					    readable_mem.string.concrete)
				except Exception as e:
					blk = self.proj.factory.block(codeaddr)

					pythonInt = hex(self.start_state.solver.eval(mem_addr))

					inst_list = []
					str_list = []

					for ins in blk.capstone.insns:
						inst_list.append(ins.op_str)



					if "unknown_tracking" not in results[funcName][file_func]:
						results[funcName][file_func]["unknown_tracking"] = []

					results[funcName][file_func]["unknown_tracking"].append(("Could not read stack variable at address " +
                                          str(pythonInt) + " - Angr couldn't concretize memory. starting at address " + str(hex(codeaddr))))

		self.ignored_json_keys = ["params", "returns", "strings", "class"]
		for jniFunc in self.nodefs:

			if jniFunc not in results:
				results[jniFunc] = {}

			for file_function in self.nodefs[jniFunc]:

				if file_function in self.ignored_json_keys:
					continue

				if file_function not in results[jniFunc]:
					results[jniFunc][file_function] = {}

					if "no_defs" not in results[jniFunc][file_function]:
						results[jniFunc][file_function]["no_defs"] = set()

					if "predecessors" not in results[jniFunc][file_function]:
						results[jniFunc][file_function]["predecessors"] = set()

					if "params" not in results[jniFunc]:
						results[jniFunc]["params"] = []

					if "returns" not in results[jniFunc]:
						results[jniFunc]["returns"] = str()

					if "strings" not in results[jniFunc]:
						results[jniFunc]["strings"] = set()

					if "class" not in results[jniFunc]:
						results[jniFunc]["class"] = str()

					results[jniFunc][file_function]["no_defs"].update(self.nodefs[jniFunc][file_function]["results"])
					results[jniFunc][file_function]["predecessors"].update(self.nodefs[jniFunc][file_function]["predecessors"])

					results[jniFunc]["params"].extend(self.nodefs[jniFunc]["params"])
					results[jniFunc]["returns"] = self.nodefs[jniFunc]["returns"]
					results[jniFunc]["class"] = self.nodefs[jniFunc]["class"]
					results[jniFunc]["strings"].update(self.nodefs[jniFunc]["strings"])

		self.nodefs = {}

		return results

# Find all binaries in the target directory.
target_dir = ""
binaries = []


if len(sys.argv) == 1:
	binaries = Path(target_dir).glob('**/*.so')
else:
	binaries = sys.argv[1:]

entireParsedResults = []
for binary in binaries:
	print("checking " + str(binary))

	fred = FReD(binary)

	if fred.proj == None:
		continue

	if fred.findJNIMethods() == 0:
		continue

	fname = Path(fred.proj.filename).name

	for ptr, funcName in fred.func_ptrs.items():

		if fred.findFileFunctionsForJNICall(ptr, funcName) == 0:
			continue

		results = fred.dataFlowAnalysis()

		file = "results/" + fname + ".json"

		parsedResults = fred.mergeResults(fred.parseResults(results), file)

		if len(parsedResults) > 0:
			entireParsedResults.append(parsedResults)

	if len(entireParsedResults) > 0:
		exception = False

		entireParsedResults = fred.padPreviousResults(entireParsedResults, file)

		with open(file, "w") as file:
			try:
				file.write(json.dumps(entireParsedResults, indent=4, sort_keys=True, default=serialize_sets, check_circular=True))
			except Exception as e:
				print(entireParsedResults)
				print(e)
				exception = True
		if exception:
			os.remove("results/" + fname + ".json")
