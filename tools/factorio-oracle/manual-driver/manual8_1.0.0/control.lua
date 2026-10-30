local function emit(t) localised_print('MANUAL '..game.table_to_json(t)) end
local function entity(e)
 if not e or not e.valid then return nil end
 return {name=e.name,type=e.type,unit=e.unit_number,position=e.position,health=e.health,orientation=e.orientation}
end
local function riding(e)
 local ok,value=pcall(function() return e.riding_state end)
 return {ok=ok,value=ok and value or nil,error=not ok and tostring(value) or nil}
end
local function set_riding(c,acc,tick)
 local value={acceleration=acc,direction=defines.riding.direction.straight}
 local ok,error=pcall(function() c.riding_state=value end)
 emit({kind='command',tick=tick,acceleration=acc,ok=ok,error=not ok and tostring(error) or nil,driver_riding=riding(c)})
end
script.on_init(function()
 local s=game.create_surface('manual8',{seed=12345,water='none',starting_area='none',autoplace_controls={['enemy-base']={frequency='none',size='none',richness='none'}}})
 for y=-384,96,32 do s.request_to_generate_chunks({0,y},0) end;s.force_generate_chunk_requests()
 local rails=0;for y=-350,80,2 do if s.create_entity{name='straight-rail',position={0,y},direction=defines.direction.north,force='player'} then rails=rails+1 end end
 local l=s.create_entity{name='locomotive',position={0,0},direction=defines.direction.north,force='player'}
 local c=s.create_entity{name='character',position={5,5},force='player'}
 if not l or not c then emit({kind='setup_error',loco=l~=nil,character=c~=nil,rails=rails});return end
 l.get_fuel_inventory().insert{name='coal',count=30}
 local ok,err=pcall(function() l.set_driver(c) end)
 global.loco=l;global.character=c
 emit({kind='setup',rails=rails,loco=entity(l),character=entity(c),set_driver_ok=ok,set_driver_error=not ok and tostring(err) or nil,actual_driver=entity(l.get_driver()),weight=l.train.weight,braking_force=l.prototype.braking_force,friction_force=l.prototype.friction_force,air_resistance=l.prototype.air_resistance,bonus=l.force.train_braking_force_bonus,defines={nothing=defines.riding.acceleration.nothing,accelerating=defines.riding.acceleration.accelerating,braking=defines.riding.acceleration.braking,straight=defines.riding.direction.straight}})
end)
script.on_event(defines.events.on_tick,function(e)
 local l=global.loco;local c=global.character;if not l or not l.valid or not c.valid then return end
 if e.tick==0 then l.train.manual_mode=true;set_riding(c,defines.riding.acceleration.accelerating,e.tick) end
 if e.tick==120 then set_riding(c,defines.riding.acceleration.nothing,e.tick) end
 if e.tick==140 then set_riding(c,defines.riding.acceleration.braking,e.tick) end
 local b=l.burner
 emit({kind='trace',tick=e.tick,loco=entity(l),driver=entity(l.get_driver()),driver_riding=riding(c),train_riding=l.train.riding_state,manual_mode=l.train.manual_mode,state=l.train.state,speed=l.train.speed,fuel=b.remaining_burning_fuel,heat=b.heat,burning=b.currently_burning and b.currently_burning.name,coal=l.get_fuel_inventory().get_item_count('coal')})
end)
