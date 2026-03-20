import urllib.request, urllib.error, json
req = urllib.request.Request(
    'https://wallet.dranzo.com/v1/experts/bank-details', 
    data=json.dumps({
        'bankName':'test',
        'accountNumber':'test',
        'ifscCode':'test',
        'accountHolderName':'test',
        'branchName':'test'
    }).encode('utf-8'), 
    headers={'Content-Type':'application/json', 'User-Agent':'PostmanRuntime/7.43.0'}, 
    method='POST'
)
try:
    res = urllib.request.urlopen(req)
    print(res.read().decode('utf-8'))
except urllib.error.HTTPError as e:
    try:
        data = e.read().decode('utf-8')
        parsed = json.loads(data)
        print(json.dumps(parsed, indent=2))
    except Exception as ex:
        print(data)
