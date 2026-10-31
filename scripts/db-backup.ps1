<#
租户库备份 / 恢复演练脚本（阶段五：每租户 pg_dump + 平台库配置备份，恢复后能登录）

用法（需 PostgreSQL 客户端 pg_dump / pg_restore / psql 在 PATH 中）：

  备份全部（平台库 + 所有 active 租户库）：
    $env:PGPASSWORD = 'workeasy123'
    .\scripts\db-backup.ps1 -Mode backup

  恢复某个租户到新库（演练不覆盖线上库）：
    .\scripts\db-backup.ps1 -Mode restore -TenantCode demo -TargetDb we_tenant_demo_restore

  只恢复平台库：
    .\scripts\db-backup.ps1 -Mode restore -PlatformOnly

恢复后把目标库写进 tenant 表（或用 -Mode restore 输出的提示）即可用原账号登录验证。
#>
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('backup', 'restore')]
    [string]$Mode,

    [string]$OutDir = (Join-Path $PSScriptRoot '..\backup'),
    [string]$PgHost = $(if ($env:PGHOST) { $env:PGHOST } else { 'localhost' }),
    [int]$PgPort = $(if ($env:PGPORT) { [int]$env:PGPORT } else { 5432 }),
    [string]$PgUser = $(if ($env:PGUSER) { $env:PGUSER } else { 'workeasy' }),
    [string]$PlatformDb = $(if ($env:PLATFORM_DB_NAME) { $env:PLATFORM_DB_NAME } else { 'workeasy_platform' }),
    [string]$TenantCode = '',
    [string]$TargetDb = '',
    [switch]$PlatformOnly,
    [int]$KeepDays = 14
)

$ErrorActionPreference = 'Stop'

function Assert-Tool([string]$name) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
        throw "未找到 $name，请安装 PostgreSQL 客户端并把 bin 目录加入 PATH"
    }
}

function Invoke-Psql([string]$database, [string]$sql) {
    $out = & psql -h $PgHost -p $PgPort -U $PgUser -d $database -Atc $sql
    if ($LASTEXITCODE -ne 0) { throw "psql 执行失败：$sql" }
    return $out
}

function New-TimestampFolder {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $dir = Join-Path $OutDir $stamp
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    return $dir
}

function Backup-All {
    Assert-Tool 'pg_dump'
    Assert-Tool 'psql'
    $dir = New-TimestampFolder
    $manifest = @()

    $platformFile = Join-Path $dir "$PlatformDb.dump"
    & pg_dump -h $PgHost -p $PgPort -U $PgUser -Fc -d $PlatformDb -f $platformFile
    if ($LASTEXITCODE -ne 0) { throw "平台库备份失败：$PlatformDb" }
    $manifest += [pscustomobject]@{ kind = 'platform'; code = 'platform'; db = $PlatformDb; file = $platformFile }
    Write-Host "平台库已备份 -> $platformFile"

    $rows = Invoke-Psql $PlatformDb "SELECT code || '|' || db_name FROM tenant WHERE status = 'active'"
    foreach ($row in ($rows -split "`n" | Where-Object { $_ -and $_.Trim() })) {
        $parts = $row.Trim().Split('|')
        $code = $parts[0]
        $dbName = $parts[1]
        $file = Join-Path $dir "$dbName.dump"
        & pg_dump -h $PgHost -p $PgPort -U $PgUser -Fc -d $dbName -f $file
        if ($LASTEXITCODE -ne 0) { throw "租户库备份失败：$code / $dbName" }
        $manifest += [pscustomobject]@{ kind = 'tenant'; code = $code; db = $dbName; file = $file }
        Write-Host "租户 $code 已备份 -> $file"
    }

    $manifestPath = Join-Path $dir 'manifest.json'
    $manifest | ConvertTo-Json -Depth 4 | Set-Content -Path $manifestPath -Encoding UTF8

    # 清理过期备份
    $limit = (Get-Date).AddDays(-$KeepDays)
    Get-ChildItem -Path $OutDir -Directory |
        Where-Object { $_.LastWriteTime -lt $limit } |
        ForEach-Object {
            Write-Host "清理过期备份：$($_.Name)"
            Remove-Item $_.FullName -Recurse -Force
        }

    Write-Host "备份完成：$dir（manifest.json 记录了各库 dump 文件）"
}

function Restore-One([string]$dumpFile, [string]$targetDatabase) {
    Assert-Tool 'pg_restore'
    Assert-Tool 'psql'
    if (-not (Test-Path $dumpFile)) { throw "备份文件不存在：$dumpFile" }

    # 演练一律恢复到新库，避免误覆盖线上库
    Invoke-Psql 'postgres' "DROP DATABASE IF EXISTS `"$targetDatabase`"" | Out-Null
    Invoke-Psql 'postgres' "CREATE DATABASE `"$targetDatabase`"" | Out-Null
    & pg_restore -h $PgHost -p $PgPort -U $PgUser -d $targetDatabase --no-owner --role $PgUser $dumpFile
    if ($LASTEXITCODE -ne 0) { throw "恢复失败：$dumpFile -> $targetDatabase" }
    Write-Host "已恢复 $dumpFile -> $targetDatabase"

    # 恢复后做一次最小可登录校验：users 表存在且有可用账号
    $users = Invoke-Psql $targetDatabase "SELECT count(*) FROM users WHERE status = 'active'"
    Write-Host "目标库 $targetDatabase 可登录账号数：$users"
}

function Restore-All {
    $latest = Get-ChildItem -Path $OutDir -Directory | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($null -eq $latest) { throw "找不到备份目录：$OutDir" }
    $manifestPath = Join-Path $latest.FullName 'manifest.json'
    if (-not (Test-Path $manifestPath)) { throw "缺少 manifest.json：$manifestPath" }
    $manifest = Get-Content $manifestPath -Raw | ConvertFrom-Json

    if ($PlatformOnly) {
        $p = $manifest | Where-Object { $_.kind -eq 'platform' } | Select-Object -First 1
        Restore-One $p.file $PlatformDb
        return
    }
    if (-not $TenantCode) { throw "请用 -TenantCode 指定租户，或加 -PlatformOnly" }

    $item = $manifest | Where-Object { $_.kind -eq 'tenant' -and $_.code -eq $TenantCode } | Select-Object -First 1
    if ($null -eq $item) { throw "备份中找不到租户：$TenantCode" }
    $target = if ($TargetDb) { $TargetDb } else { "$($item.db)_restore" }
    Restore-One $item.file $target
    Write-Host "恢复完成。将 tenant 表指向新库后可登录验证："
    Write-Host "  UPDATE tenant SET db_name = '$target' WHERE code = '$TenantCode';"
}

switch ($Mode) {
    'backup' { Backup-All }
    'restore' { Restore-All }
}
