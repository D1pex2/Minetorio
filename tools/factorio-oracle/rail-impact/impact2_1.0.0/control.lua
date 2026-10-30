local function emit(t) localised_print("IMPACT "..game.table_to_json(t)) end
local function ent(e)
 if not e or not e.valid then return nil end
 return {name=e.name,unit=e.unit_number,health=e.health,position=e.position}
end
script.on_init(function()
 local s=game.create_surface('impact-oracle',{seed=12345,water='none',starting_area='none',autoplace_controls={['enemy-base']={frequency='none',size='none',richness='none'}}})
 for x=0,128,32 do for y=-800,64,32 do s.request_to_generate_chunks({x,y},0) end end
 s.force_generate_chunk_requests()
 global.cases={}
 for i,dist in ipairs({70,200,500,70,200,500}) do
  local x=i*16
  local rails=0
  for y=-750,40,2 do if s.create_entity{name='straight-rail',position={x,y},direction=defines.direction.north,force='player'} then rails=rails+1 end end
  local l=s.create_entity{name='locomotive',position={x,0},direction=defines.direction.north,force='player'}
  local a=s.create_entity{name='train-stop',position={x+2,-700},direction=defines.direction.north,force='player'}
  local target_name = i<=3 and 'stone-wall' or 'small-biter'
  local wall=s.create_entity{name=target_name,position={x+1,-dist},force=i<=3 and 'neutral' or 'enemy'}
  if wall and i>3 then wall.active=false end
  if not l or not a or not wall then emit({kind='setup_error',case=i,rails=rails,loco=l~=nil,station=a~=nil,wall=wall~=nil}); return end
  a.backer_name='Impact'..i
  local fuel=l.get_fuel_inventory().insert{name='coal',count=30}
  global.cases[i]={loco=l,wall=wall,station=a}
  emit({kind='setup',case=i,rails=rails,fuel=fuel,loco=ent(l),wall=ent(wall),station=ent(a),wall_mask=wall.prototype.collision_mask,loco_mask=l.prototype.collision_mask,wall_resistances=wall.prototype.resistances,wall_max_health=wall.prototype.max_health,wall_active=wall.active,loco_resistances=l.prototype.resistances,weight=l.train.weight})
 end
end)
script.on_event(defines.events.on_entity_damaged,function(e)
 emit({kind='damage',tick=e.tick,entity=ent(e.entity),cause=ent(e.cause),damage_type=e.damage_type.name,original=e.original_damage_amount,final=e.final_damage_amount,final_health=e.final_health})
end)
script.on_event(defines.events.on_entity_died,function(e)
 emit({kind='died',tick=e.tick,entity=ent(e.entity),cause=ent(e.cause),damage_type=e.damage_type and e.damage_type.name})
end)
script.on_event(defines.events.on_tick,function(e)
 for i,c in ipairs(global.cases or {}) do
  local l=c.loco
  if e.tick==0 and l.valid then l.train.schedule={current=1,records={{station='Impact'..i,wait_conditions={{type='time',compare_type='and',ticks=60}}}}}; l.train.manual_mode=false end
  if l.valid then local t=l.train; emit({kind='trace',case=i,tick=e.tick,speed=t.speed,state=t.state,has_path=t.has_path,loco=ent(l),wall=ent(c.wall)})
  else emit({kind='trace',case=i,tick=e.tick,loco_dead=true,wall=ent(c.wall)}) end
 end
end)
