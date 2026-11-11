param(
    [string]$BaseUri = 'http://localhost:8090/example/v1'
)

$ErrorActionPreference = 'Stop'

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

Write-Host "Health: $BaseUri/health"
$health = Invoke-RestMethod -Uri "$BaseUri/health" -Method Get
Assert-True ($health.status -eq 'UP') 'Health status is not UP.'

$createBody = @{
    name = 'PowerShell Smoke Hotel'
    description = 'Created by scripts/smoke-test.ps1'
    city = 'Tokyo'
    rating = 4
} | ConvertTo-Json

Write-Host 'Create hotel'
$createResponse = Invoke-WebRequest -Uri "$BaseUri/hotels" -Method Post -ContentType 'application/json' -Body $createBody
Assert-True ($createResponse.StatusCode -eq 201) 'POST did not return 201.'
$location = $createResponse.Headers['Location']
Assert-True (-not [string]::IsNullOrWhiteSpace($location)) 'Location header is missing.'
$hotelId = [long]($location -split '/')[-1]
$hotelUri = "$BaseUri/hotels/$hotelId"

try {
    Write-Host "Get hotel: $hotelId"
    $hotel = Invoke-RestMethod -Uri $hotelUri -Method Get
    Assert-True ($hotel.name -eq 'PowerShell Smoke Hotel') 'GET returned an unexpected hotel.'

    Write-Host 'Update hotel'
    $updateBody = @{
        id = $hotelId
        name = 'PowerShell Smoke Hotel Updated'
        description = 'Updated by smoke test'
        city = 'Kyoto'
        rating = 5
    } | ConvertTo-Json
    $updateResponse = Invoke-WebRequest -Uri $hotelUri -Method Put -ContentType 'application/json' -Body $updateBody
    Assert-True ($updateResponse.StatusCode -eq 204) 'PUT did not return 204.'

    Write-Host 'List hotels and increment metric'
    $page = Invoke-RestMethod -Uri "$BaseUri/hotels?page=0&size=100" -Method Get
    Assert-True (($page.content | Where-Object { $_.id -eq $hotelId }).Count -eq 1) 'Created hotel is not in the page.'

    $metrics = Invoke-RestMethod -Uri "$BaseUri/metrics" -Method Get
    $counter = $metrics.'Khoubyari.HotelService.getAll.largePayload'
    Assert-True ($counter -ge 1) 'Large-payload metric was not incremented.'
}
finally {
    Write-Host 'Delete hotel'
    Invoke-WebRequest -Uri $hotelUri -Method Delete | Out-Null
}

Write-Host 'Smoke test passed.' -ForegroundColor Green
