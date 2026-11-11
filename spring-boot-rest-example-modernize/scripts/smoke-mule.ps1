param([string]$BaseUrl = 'http://localhost:8090/example/v1')
$ErrorActionPreference = 'Stop'
function Assert-Equal($expected, $actual, [string]$label) {
    if ($expected -ne $actual) { throw "$label : expected $expected, received $actual" }
}
function Get-Status([string]$Method, [string]$Uri, [string]$Body = '') {
    try {
        $options = @{ UseBasicParsing = $true; Method = $Method; Uri = $Uri }
        if ($Body) { $options.ContentType = 'application/json'; $options.Body = $Body }
        return [int](Invoke-WebRequest @options).StatusCode
    } catch {
        if ($null -eq $_.Exception.Response) { throw }
        return [int]$_.Exception.Response.StatusCode
    }
}
$BaseUrl = $BaseUrl.TrimEnd('/')
$hotelUrl = $null
try {
    Assert-Equal 'UP' (Invoke-RestMethod "$BaseUrl/health").status 'Health'
    $body = @{name='Mule Java 17 Hotel'; description='Smoke test'; city='Tokyo'; rating=4} | ConvertTo-Json
    $created = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$BaseUrl/hotels" -ContentType 'application/json' -Body $body
    Assert-Equal 201 $created.StatusCode 'Create'
    $hotelUrl = ([uri]::new([uri]"$BaseUrl/hotels", [string]$created.Headers['Location'])).AbsoluteUri
    $hotel = Invoke-RestMethod $hotelUrl
    Assert-Equal 'Mule Java 17 Hotel' $hotel.name 'JSON GET'
    $xml = Invoke-WebRequest -UseBasicParsing -Uri $hotelUrl -Headers @{Accept='application/xml'}
    Assert-Equal 200 $xml.StatusCode 'XML GET'
    if ($xml.Content -notmatch '<name>Mule Java 17 Hotel</name>') { throw 'XML response mismatch' }
    $hotel.rating = 5
    $updated = Invoke-WebRequest -UseBasicParsing -Method Put -Uri $hotelUrl -ContentType 'application/json' -Body ($hotel | ConvertTo-Json)
    Assert-Equal 204 $updated.StatusCode 'Update'
    Assert-Equal 5 (Invoke-RestMethod $hotelUrl).rating 'Updated rating'
    $badId = $hotel.id + 1
    $badBody = @{id=$badId; name='Wrong ID'; rating=1} | ConvertTo-Json
    Assert-Equal 400 (Get-Status 'PUT' $hotelUrl $badBody) 'ID mismatch'
    Assert-Equal 400 (Get-Status 'GET' "$BaseUrl/hotels?page=-1") 'Invalid pagination'
    Assert-Equal 400 (Get-Status 'POST' "$BaseUrl/hotels" '{"rating":1}') 'Missing name'
    Assert-Equal 405 (Get-Status 'PATCH' $hotelUrl) 'Unsupported method'
    $page = Invoke-RestMethod "$BaseUrl/hotels?page=0&size=100"
    if ($page.totalElements -lt 1) { throw 'List did not return stored hotels' }
    $metrics = Invoke-RestMethod "$BaseUrl/metrics"
    if ($metrics.'Khoubyari.HotelService.getAll.largePayload' -lt 1) { throw 'Metrics counter missing' }
    Assert-Equal 204 (Get-Status 'DELETE' $hotelUrl) 'Delete'
    Assert-Equal 404 (Get-Status 'GET' $hotelUrl) 'Missing resource'
    $hotelUrl = $null
    Write-Output 'PASS: health, Java Module CRUD, JSON/XML, pagination, metrics, HTTP 400/404/405'
} finally {
    if ($hotelUrl) { Get-Status 'DELETE' $hotelUrl | Out-Null }
}
