function Join-BackupBytes([byte[][]]$Parts) {
    $length = ($Parts | ForEach-Object { $_.Length } | Measure-Object -Sum).Sum
    $result = New-Object byte[] $length
    $offset = 0
    foreach ($part in $Parts) {
        [Array]::Copy($part, 0, $result, $offset, $part.Length)
        $offset += $part.Length
    }
    return $result
}

function Get-BackupKeyMaterial([string]$Password, [byte[]]$Salt) {
    $derive = [System.Security.Cryptography.Rfc2898DeriveBytes]::new(
        $Password,
        $Salt,
        200000,
        [System.Security.Cryptography.HashAlgorithmName]::SHA256)
    try {
        return $derive.GetBytes(64)
    }
    finally {
        $derive.Dispose()
    }
}

function Test-BackupMac([byte[]]$Expected, [byte[]]$Actual) {
    if ($Expected.Length -ne $Actual.Length) {
        return $false
    }
    $difference = 0
    for ($index = 0; $index -lt $Expected.Length; $index++) {
        $difference = $difference -bor ($Expected[$index] -bxor $Actual[$index])
    }
    return $difference -eq 0
}

function Protect-PaymentBackupFile([string]$InputPath, [string]$OutputPath, [string]$Password) {
    $magic = [Text.Encoding]::ASCII.GetBytes("PMBK1")
    $salt = New-Object byte[] 16
    $iv = New-Object byte[] 16
    $rng = New-Object System.Security.Cryptography.RNGCryptoServiceProvider
    try {
        $rng.GetBytes($salt)
        $rng.GetBytes($iv)
    }
    finally {
        $rng.Dispose()
    }
    $material = Get-BackupKeyMaterial $Password $salt
    $aes = [System.Security.Cryptography.Aes]::Create()
    try {
        $aes.Mode = [System.Security.Cryptography.CipherMode]::CBC
        $aes.Padding = [System.Security.Cryptography.PaddingMode]::PKCS7
        $aes.Key = $material[0..31]
        $aes.IV = $iv
        $encryptor = $aes.CreateEncryptor()
        try {
            $plain = [IO.File]::ReadAllBytes($InputPath)
            $ciphertext = $encryptor.TransformFinalBlock($plain, 0, $plain.Length)
        }
        finally {
            $encryptor.Dispose()
        }
    }
    finally {
        $aes.Dispose()
    }
    $authenticated = Join-BackupBytes @($magic, $salt, $iv, $ciphertext)
    $hmac = [System.Security.Cryptography.HMACSHA256]::new([byte[]]$material[32..63])
    try {
        $mac = $hmac.ComputeHash($authenticated)
    }
    finally {
        $hmac.Dispose()
    }
    [IO.File]::WriteAllBytes($OutputPath, (Join-BackupBytes @($authenticated, $mac)))
}

function Unprotect-PaymentBackupFile([string]$InputPath, [string]$OutputPath, [string]$Password) {
    $payload = [IO.File]::ReadAllBytes($InputPath)
    if ($payload.Length -lt 69) {
        throw "Encrypted backup is too small"
    }
    $magic = [Text.Encoding]::ASCII.GetString($payload, 0, 5)
    if ($magic -ne "PMBK1") {
        throw "Unsupported encrypted backup format"
    }
    $salt = $payload[5..20]
    $iv = $payload[21..36]
    $cipherLength = $payload.Length - 37 - 32
    $ciphertext = $payload[37..(36 + $cipherLength)]
    $expectedMac = $payload[(37 + $cipherLength)..($payload.Length - 1)]
    $material = Get-BackupKeyMaterial $Password $salt
    $authenticated = Join-BackupBytes @(
        [Text.Encoding]::ASCII.GetBytes("PMBK1"),
        $salt,
        $iv,
        $ciphertext
    )
    $hmac = [System.Security.Cryptography.HMACSHA256]::new([byte[]]$material[32..63])
    try {
        $actualMac = $hmac.ComputeHash($authenticated)
    }
    finally {
        $hmac.Dispose()
    }
    if (-not (Test-BackupMac $actualMac $expectedMac)) {
        throw "Encrypted backup integrity check failed"
    }
    $aes = [System.Security.Cryptography.Aes]::Create()
    try {
        $aes.Mode = [System.Security.Cryptography.CipherMode]::CBC
        $aes.Padding = [System.Security.Cryptography.PaddingMode]::PKCS7
        $aes.Key = $material[0..31]
        $aes.IV = $iv
        $decryptor = $aes.CreateDecryptor()
        try {
            $plain = $decryptor.TransformFinalBlock($ciphertext, 0, $ciphertext.Length)
        }
        finally {
            $decryptor.Dispose()
        }
    }
    finally {
        $aes.Dispose()
    }
    [IO.File]::WriteAllBytes($OutputPath, $plain)
}
