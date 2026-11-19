-- KEYS[1]: SKU hash key (e.g., "sku:IPHONE-18")
-- KEYS[2]: Idempotency key (e.g., "req:uuid-1234")
-- ARGV[1]: Requested quantity
-- ARGV[2]: Idempotency TTL in seconds (e.g., 300)

local cached = redis.call('GET', KEYS[2])
if cached then
    return cached
end

local stock = tonumber(redis.call('HGET', KEYS[1], 'availableStock'))
if not stock then
    return 'SKU_NOT_FOUND'
end

local reqQty = tonumber(ARGV[1])
if stock < reqQty then
    redis.call('SETEX', KEYS[2], ARGV[2], 'OUT_OF_STOCK')
    return 'OUT_OF_STOCK'
end

local initialStock = tonumber(redis.call('HGET', KEYS[1], 'initialStock'))
local basePrice = tonumber(redis.call('HGET', KEYS[1], 'basePrice'))
local lockedPrice = tonumber(redis.call('HGET', KEYS[1], 'currentPrice'))

local newStock = stock - reqQty
local nextPrice = basePrice
if (newStock / initialStock) < 0.20 then
    nextPrice = basePrice * 1.20
end

redis.call('HSET', KEYS[1], 'availableStock', newStock, 'currentPrice', nextPrice)

local result = 'SUCCESS:' .. tostring(lockedPrice)
redis.call('SETEX', KEYS[2], ARGV[2], result)
return result
