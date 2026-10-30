local function emit(t) localised_print('MEETING '..game.table_to_json(t)) end
local function ent(e)
 if not e or not e.valid then return nil end
 local t=(e.type=='locomotive' or e.type=='cargo-wagon') and e.train or nil
 return {name=e.name,unit=e.unit_number,health=e.health,position=e.position,orientation=e.orientation,speed=t and t.speed,weight=t and t.weight,state=t and t.state,path=t and t.has_path}
end
local function pair(c) return {a=ent(c.a),b=ent(c.b),trailing=ent(c.trailing)} end
script.on_init(function()
 local s=game.create_surface('meeting6',{seed=12345,water='none',starting_area='none',autoplace_controls={['enemy-base']={frequency='none',size='none',richness='none'}}})
 for y=-768,320,32 do s.request_to_generate_chunks({32,y},1) end
 for x=64,224,32 do s.request_to_generate_chunks({x,0},1) end
 s.force_generate_chunk_requests(); global.cases={}; global.resists={}
 for i=1,2 do
  local x=i*16
  for y=-720,220,2 do s.create_entity{name='straight-rail',position={x,y},direction=defines.direction.north,force='player'} end
  local a=s.create_entity{name='locomotive',position={x,0},direction=defines.direction.north,force='player'}
  local b=s.create_entity{name='locomotive',position={x,-400},direction=defines.direction.south,force='player'}
  local trailing=i==2 and s.create_entity{name='cargo-wagon',position={x,7},direction=defines.direction.north,force='player'} or nil
  local sa=s.create_entity{name='train-stop',position={x+2,-700},direction=defines.direction.north,force='player'}
  local sb=s.create_entity{name='train-stop',position={x-2,200},direction=defines.direction.south,force='player'}
  if not a or not b or not sa or not sb or (i==2 and not trailing) then emit({kind='setup_error',case=i}); return end
  sa.backer_name='North'..i; sb.backer_name='South'..i
  a.get_fuel_inventory().insert{name='coal',count=30}; b.get_fuel_inventory().insert{name='coal',count=30}
  global.cases[i]={a=a,b=b,trailing=trailing,sa=sa,sb=sb}
  emit({kind='setup',case=i,pair=pair(global.cases[i]),sa=ent(sa),sa_rail=ent(sa.connected_rail),sb=ent(sb),sb_rail=ent(sb.connected_rail)})
 end
 for i,amount in ipairs({0.001,0.1,0.5,1,1.01,49,50,51,52,100}) do
  local x=64+i*16
  for y=-6,6,2 do s.create_entity{name='straight-rail',position={x,y},direction=defines.direction.north,force='player'} end
  local e=s.create_entity{name='cargo-wagon',position={x,0},direction=defines.direction.north,force='player'}
  if not e then emit({kind='resist_setup_error',case=i}); return end
  global.resists[i]={target=e,amount=amount}
 end
end)
script.on_event(defines.events.on_entity_damaged,function(e)
 local cases={}; for i,c in ipairs(global.cases or {}) do cases[i]=pair(c) end
 emit({kind='damage',tick=e.tick,entity=ent(e.entity),cause=ent(e.cause),damage_type=e.damage_type.name,original=e.original_damage_amount,final=e.final_damage_amount,final_health=e.final_health,pairs=cases})
end)
script.on_event(defines.events.on_entity_died,function(e) emit({kind='died',tick=e.tick,entity=ent(e.entity),cause=ent(e.cause),damage_type=e.damage_type and e.damage_type.name}) end)
script.on_event(defines.events.on_tick,function(e)
 if e.tick==0 then
  for i,c in ipairs(global.cases) do
   c.a.train.schedule={current=1,records={{station='North'..i,wait_conditions={{type='time',compare_type='and',ticks=60}}}}}; c.a.train.manual_mode=false
   c.b.train.schedule={current=1,records={{station='South'..i,wait_conditions={{type='time',compare_type='and',ticks=60}}}}}; c.b.train.manual_mode=false
  end
  for i,c in ipairs(global.resists) do local before=c.target.health; local result=c.target.damage(c.amount,game.forces.player,'impact'); emit({kind='resistance',case=i,amount=c.amount,unit=c.target.unit_number,before=before,after=c.target.health,result=result}) end
 end
 for i,c in ipairs(global.cases or {}) do emit({kind='trace',case=i,tick=e.tick,pair=pair(c)}) end
end)
