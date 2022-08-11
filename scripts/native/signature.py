#!/usr/bin/env python

import re
import sys
import traceback

JNI_METHOD_SIGNATURE_MAP = {
    #basic type
    'V': 'void',
    'Z': 'boolean',
    'I': 'int',
    'J': 'long',
    'D': 'double',
    'F': 'float',
    'B': 'byte',
    'C': 'char',
    'S': 'short',
    # #array type
    # 'int[]': '[I',
    # 'float[]': '[F',
    # 'byte[]': '[B',
    # 'char[]': '[C',
    # 'short[]': '[S',
    # 'double[]': '[D',
    # 'long[]': '[J',
    # 'boolean[]': '[Z',
}

class SignatureParser:
	def __init__(self, signature):
		#print("Parsing signature " + signature)

		self.params = re.findall("^\(.*\)", signature)

		if len(self.params) > 0:
			self.params = self.params[0][1:-1]
		else:
			print("Invalid signature")
			return

		self.returns = re.findall("\)\[*[ZBCSIJFVD]|\)\[*L.*?$", signature)

		if len(self.returns) > 0:
			self.returns = self.returns[0][1:]

		self.param_list = []

		self.parseParams()
		self.parseReturns()

	def parseParams(self):

		if len(self.param_list) > 0:
			return self.param_list

		# Find L params first.
		objParams = re.findall("\[*[ZBCSIJFVD](?=.*\))|\[*L.*?;(?=.*\))", self.params)

		for param in objParams:
			self.param_list.append(self.parseSingleParam(param))

		return self.param_list

	def parseSingleParam(self, param):

		try:
			if param in JNI_METHOD_SIGNATURE_MAP:
				return JNI_METHOD_SIGNATURE_MAP[param]
			else:
				# Either L or an array
				if param[0] == 'L':
					# Cut out the L and the ;
					return param[1:-1]
				elif param[0] == '[':
					return self.parseSingleParam(param[1:]) + "[]"
		except Exception as e:
			traceback.print_exc()

	def parseReturns(self):
		self.returns = self.parseSingleParam(self.returns)
		return self.returns
